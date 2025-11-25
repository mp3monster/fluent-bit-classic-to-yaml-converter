#!/usr/bin/env python3
"""
Fluent Bit Classic Config to YAML Converter
Features:
  - @INCLUDE, @PARSER_FILE, Lua scripts
  - Key/value mapping & transformations
  - External parser files
  - JSON schema validation
  - Full debug logging via Python logging
"""

import os
import json
import argparse
import glob
import re
import sys
import logging
from pathlib import Path
from typing import List, Dict, Any, Optional, Set

from ruamel.yaml import YAML
from ruamel.yaml.comments import CommentedMap, CommentedSeq
from ruamel.yaml.scalarstring import LiteralScalarString
from ruamel.yaml.tokens import CommentToken
from ruamel.yaml.error import CommentMark
import jsonschema
from jsonschema import ValidationError


# === Logging Setup ===
def setup_logging(verbosity: int) -> logging.Logger:
    """
    Configure logging with appropriate level and format.
    verbosity: 0=WARNING, 1=INFO, 2=DEBUG
    """
    logger = logging.getLogger('fluentbit_converter')
    logger.setLevel(logging.DEBUG)  # Always capture all

    # Console handler
    console_handler = logging.StreamHandler(sys.stderr)
    level = {0: logging.WARNING, 1: logging.INFO, 2: logging.DEBUG}[verbosity]
    console_handler.setLevel(level)

    # Formatter
    formatter = logging.Formatter(
        fmt='%(asctime)s [%(levelname)s] %(message)s',
        datefmt='%H:%M:%S'
    )
    console_handler.setFormatter(formatter)

    # Avoid duplicate handlers
    if not logger.handlers:
        logger.addHandler(console_handler)

    return logger


# === Constants ===
SIZE_UNIT_CONVERSION = {
    'kb': 1024, 'mb': 1024**2, 'gb': 1024**3, 'tb': 1024**4,
    'k': 1000, 'm': 1000**2, 'g': 1000**3, 't': 1000**4,
}

SECTION_MAPPING = {
    'input': 'inputs',
    'filter': 'filters',
    'output': 'outputs',
    'parser': 'parsers',
    'multiline_parser': 'multiline_parsers',
}

ORDERED_TOP_LEVEL_KEYS = [
    'service', 'inputs', 'filters', 'parsers', 'multiline_parsers', 'outputs'
]


# === Utility Functions ===
def convert_size_to_bytes(size_string: str, logger: logging.Logger) -> str:
    """Convert human-readable size to bytes."""
    logger.debug(f"Converting size: {size_string}")
    normalized = size_string.strip().lower()
    for unit, multiplier in SIZE_UNIT_CONVERSION.items():
        if normalized.endswith(unit):
            numeric_part = normalized[:-len(unit)]
            if numeric_part.replace('.', '').isdigit():
                result = str(int(float(numeric_part) * multiplier))
                logger.debug(f"  -> {result} bytes")
                return result
    logger.debug(f"  -> No unit match, returning original")
    return size_string


def apply_single_transform(
    value: str,
    transform_rule: Dict[str, Any],
    logger: logging.Logger
) -> str:
    """Apply one transformation rule."""
    if not isinstance(value, str):
        return value

    transform_type = transform_rule.get("type")
    logger.debug(f"Applying transform: {transform_type} on '{value}'")

    try:
        if transform_type == "replace":
            result = value.replace(transform_rule["from"], transform_rule["to"])
            logger.debug(f"  replace: '{transform_rule['from']}' -> '{transform_rule['to']}' -> '{result}'")
            return result
        elif transform_type == "regex_replace":
            pattern = re.compile(transform_rule["pattern"])
            result = pattern.sub(transform_rule["replace"], value)
            logger.debug(f"  regex: /{transform_rule['pattern']}/ -> '{transform_rule['replace']}' -> '{result}'")
            return result
        elif transform_type == "convert_size":
            return convert_size_to_bytes(value, logger)
        elif transform_type == "bool_map":
            normalized = value.strip().lower()
            result = transform_rule["true"] if normalized in ["on", "true", "1", "yes"] else transform_rule["false"]
            logger.debug(f"  bool_map: '{value}' -> '{result}'")
            return result
    except Exception as transform_error:
        logger.warning(f"Transform failed: {transform_error}")
    return value


# === Config Merging ===
def merge_config_files(
    config_path: str,
    included_files: Set[str],
    parser_directory: Optional[str],
    logger: logging.Logger
) -> List[str]:
    """Recursively merge @INCLUDE and @PARSER_FILE."""
    absolute_path = os.path.abspath(config_path)
    logger.debug(f"Processing config file: {absolute_path}")

    if absolute_path in included_files:
        raise ValueError(f"Cycle detected in includes: {absolute_path}")
    included_files.add(absolute_path)

    try:
        with open(absolute_path, 'r', encoding='utf-8') as file_handle:
            file_lines = file_handle.readlines()
        logger.debug(f"Read {len(file_lines)} lines from {config_path}")
    except FileNotFoundError:
        raise FileNotFoundError(f"Config file not found: {config_path}")
    except Exception as read_error:
        raise RuntimeError(f"Failed to read {config_path}: {read_error}")

    merged_lines = []
    current_directory = os.path.dirname(absolute_path)

    for line_index, line in enumerate(file_lines):
        stripped_line = line.strip()
        logger.debug(f"Line {line_index + 1}: {stripped_line or '<empty>'}")

        if stripped_line.startswith('@INCLUDE'):
            include_pattern = stripped_line[9:].strip().strip("'\"")
            matched_paths = sorted(glob.glob(os.path.join(current_directory, include_pattern)))
            logger.debug(f"@INCLUDE pattern: {include_pattern} -> {len(matched_paths)} files")

            if not matched_paths:
                logger.warning(f"@INCLUDE not found: {include_pattern}")
                merged_lines.append(f"# @INCLUDE {include_pattern}  # NOT FOUND\n")
                continue

            for included_path in matched_paths:
                relative_path = os.path.relpath(included_path, current_directory)
                logger.info(f"Including: {relative_path}")
                try:
                    sub_lines = merge_config_files(
                        included_path, included_files.copy(), parser_directory, logger
                    )
                    merged_lines.append(f"# Included from {relative_path}\n")
                    merged_lines.extend(sub_lines)
                except Exception as include_error:
                    logger.error(f"Failed to include {included_path}: {include_error}")
                    merged_lines.append(f"# FAILED to include: {relative_path}  # {include_error}\n")

        elif stripped_line.startswith('@PARSER_FILE') and parser_directory:
            parser_filename = stripped_line[13:].strip().strip("'\"")
            parser_full_path = os.path.join(parser_directory, parser_filename)
            logger.debug(f"@PARSER_FILE: {parser_filename} -> {parser_full_path}")

            try:
                with open(parser_full_path, 'r', encoding='utf-8') as parser_file:
                    parser_lines = parser_file.readlines()
                relative_parser_path = os.path.relpath(parser_full_path, current_directory)
                logger.info(f"Loaded parser: {relative_parser_path} ({len(parser_lines)} lines)")
                merged_lines.append(f"# Parser from {relative_parser_path}\n")
                merged_lines.extend(parser_lines)
            except Exception as parser_error:
                logger.error(f"Failed to load parser {parser_filename}: {parser_error}")
                merged_lines.append(f"# FAILED parser: {parser_filename}\n")

        else:
            merged_lines.append(line)

    return merged_lines


# === Parsing ===
def parse_config_lines(lines: List[str], logger: logging.Logger) -> Dict[str, Any]:
    """Parse merged lines into structured config."""
    logger.info(f"Parsing {len(lines)} merged lines")
    config_data = {}
    pending_comments = []
    current_section_dict = None
    current_section_type = None

    line_index = 0
    while line_index < len(lines):
        current_line = lines[line_index]
        line_content = current_line.rstrip('\n')
        stripped_content = line_content.strip()

        if not stripped_content or stripped_content.startswith('#'):
            if stripped_content.startswith('#'):
                pending_comments.append(stripped_content[1:].strip())
            line_index += 1
            continue

        if stripped_content.startswith('[') and stripped_content.endswith(']'):
            section_name = stripped_content[1:-1].strip().lower()
            logger.debug(f"Found section: [{section_name.upper()}]")

            if section_name == 'service':
                config_data['service'] = {}
                current_section_dict = config_data['service']
                current_section_type = 'service'
            else:
                mapped_section = SECTION_MAPPING.get(section_name, section_name + 's')
                config_data.setdefault(mapped_section, [])
                current_section_dict = {}
                config_data[mapped_section].append(current_section_dict)
                current_section_type = mapped_section

            if pending_comments:
                current_section_dict['_comment'] = ' '.join(pending_comments)
                logger.debug(f"Attached {len(pending_comments)} comment(s) to section")
                pending_comments = []

            line_index += 1
            continue

        if current_section_dict is not None:
            lua_match = re.match(r'^\s*(script|call)\s+(.+)', line_content, re.I)
            if lua_match:
                key_name = lua_match.group(1).lower()
                value_start = lua_match.group(2)
                script_lines = [value_start]
                line_index += 1

                logger.debug(f"Found multi-line {key_name}: starting with '{value_start}'")

                while line_index < len(lines):
                    next_line = lines[line_index]
                    next_stripped = next_line.strip()
                    if not next_stripped or next_stripped.startswith('[') or \
                       re.match(r'^\s*(name|match|script|call)\s+', next_line, re.I):
                        break
                    script_lines.append(next_line.rstrip('\n'))
                    line_index += 1

                full_script = '\n'.join(script_lines)
                current_section_dict[key_name] = full_script
                current_section_dict[f'{key_name}_block'] = True
                logger.debug(f"Collected {len(script_lines)} lines for {key_name}")
                continue

            key_value_parts = line_content.lstrip().split(maxsplit=1)
            if len(key_value_parts) == 2:
                config_key = key_value_parts[0].lower()
                config_value = key_value_parts[1].strip()
                current_section_dict[config_key] = config_value
                logger.debug(f"Key-value: {config_key} = {config_value}")
            else:
                logger.warning(f"Invalid line in section: {line_content.strip()}")

        line_index += 1

    logger.info(f"Parsed config structure: {list(config_data.keys())}")
    return config_data


# === Mapping & Transform ===
def apply_mappings_and_transformations(
    config_data: Dict[str, Any],
    mappings_config: Dict[str, Any],
    logger: logging.Logger
) -> None:
    """Apply key renaming and value transforms."""
    mappings_root = mappings_config.get('mappings', {})
    logger.info("Applying mappings and transformations")

    # Service
    if 'service' in config_data:
        service_mappings = mappings_root.get('service', {})
        service_item = config_data['service']
        key_map = service_mappings.get('key_mappings', {})

        for old_key in list(service_item):
            if old_key in key_map and old_key != '_comment':
                new_key = key_map[old_key]
                service_item[new_key] = service_item.pop(old_key)
                if f'{old_key}_block' in service_item:
                    service_item[f'{new_key}_block'] = service_item.pop(f'{old_key}_block')
                logger.debug(f"Service key: {old_key} -> {new_key}")

        for target_key, transform_list in service_mappings.get('value_transformations', {}).items():
            if target_key in service_item and not service_item.get(f'{target_key}_block'):
                for transform_rule in transform_list:
                    old_value = service_item[target_key]
                    service_item[target_key] = apply_single_transform(old_value, transform_rule, logger)
                    logger.debug(f"Service transform: {target_key} '{old_value}' -> '{service_item[target_key]}'")

    # List sections
    for section_name in ['inputs', 'filters', 'outputs', 'parsers', 'multiline_parsers']:
        if section_name not in config_data:
            continue

        section_mappings = mappings_root.get(section_name, {})
        for section_item in config_data[section_name]:
            plugin_name = section_item.get('name', '').lower()
            plugin_config = section_mappings.get(plugin_name, {})
            key_map = plugin_config.get('key_mappings', {})

            for old_key in list(section_item):
                if old_key in key_map and old_key != '_comment':
                    new_key = key_map[old_key]
                    section_item[new_key] = section_item.pop(old_key)
                    if f'{old_key}_block' in section_item:
                        section_item[f'{new_key}_block'] = section_item.pop(f'{old_key}_block')
                    logger.debug(f"{section_name}.{plugin_name}: {old_key} -> {new_key}")

            for target_key, transform_list in plugin_config.get('value_transformations', {}).items():
                if target_key in section_item and not section_item.get(f'{target_key}_block'):
                    for transform_rule in transform_list:
                        old_value = section_item[target_key]
                        section_item[target_key] = apply_single_transform(old_value, transform_rule, logger)
                        logger.debug(f"{section_name}.{plugin_name}: {target_key} '{old_value}' -> '{section_item[target_key]}'")


# === YAML Output ===
def build_yaml_structure(config_data: Dict[str, Any], logger: logging.Logger):
    """Build final YAML structure."""
    logger.info("Building YAML output structure")
    yaml_instance = YAML()
    yaml_instance.indent(mapping=2, sequence=4, offset=2)
    root_map = CommentedMap()

    for top_level_key in ORDERED_TOP_LEVEL_KEYS:
        if top_level_key not in config_data:
            continue

        if top_level_key == 'service':
            service_data = config_data[top_level_key]
            service_map = CommentedMap()
            for key, value in service_data.items():
                if key == '_comment':
                    continue
                if service_data.get(f'{key}_block'):
                    service_map[key] = LiteralScalarString(value + '\n')
                    logger.debug(f"Service block: {key} (Lua/script)")
                else:
                    service_map[key] = value
            root_map[top_level_key] = service_map
            if '_comment' in service_data:
                comment_token = CommentToken(f"# {service_data['_comment']}\n", CommentMark(0))
                root_map.yaml_set_comment_before_after_key(top_level_key, before=comment_token)

        else:
            sequence = CommentedSeq()
            for item_index, item in enumerate(config_data[top_level_key]):
                item_map = CommentedMap()
                for key, value in item.items():
                    if key == '_comment':
                        continue
                    if item.get(f'{key}_block'):
                        item_map[key] = LiteralScalarString(value + '\n')
                        logger.debug(f"{top_level_key}[{item_index}].{key}: block scalar")
                    else:
                        item_map[key] = value
                if '_comment' in item:
                    comment_token = CommentToken(f"# {item['_comment']}\n", CommentMark(0))
                    if item_index == 0:
                        sequence.yaml_set_start_comment(comment_token)
                    else:
                        sequence.yaml_set_comment_before_after_key(item_index, before=comment_token)
                sequence.append(item_map)
            root_map[top_level_key] = sequence

    for key in config_data:
        if key not in ORDERED_TOP_LEVEL_KEYS:
            root_map[key] = config_data[key]

    return root_map, yaml_instance


# === Main ===
def main() -> None:
    parser = argparse.ArgumentParser(
        description='Convert classic Fluent Bit config to modern YAML format.',
        formatter_class=argparse.ArgumentDefaultsHelpFormatter
    )
    parser.add_argument('input_file', help='Path to main Fluent Bit .conf file')
    parser.add_argument('output_file', help='Output YAML file path')
    parser.add_argument('--mappings', help='JSON mappings configuration file', default=None)
    parser.add_argument('--schema', help='JSON schema for mappings', default='mappings_schema.json')
    parser.add_argument('--parser-dir', help='Directory containing external .parser files', default=None)
    parser.add_argument('-v', '--verbose', action='count', default=0, help='Increase verbosity (-v, -vv)')
    args = parser.parse_args()

    # Setup logging
    logger = setup_logging(min(args.verbose, 2))
    logger.info("Starting Fluent Bit config conversion")

    # Validate input
    if not os.path.isfile(args.input_file):
        logger.error(f"Input file not found: {args.input_file}")
        sys.exit(1)

    # Load mappings
    mappings_configuration = {"mappings": {}}
    if args.mappings:
        logger.info(f"Loading mappings from: {args.mappings}")
        if not os.path.isfile(args.mappings):
            logger.error(f"Mappings file not found: {args.mappings}")
            sys.exit(1)
        try:
            with open(args.mappings, 'r', encoding='utf-8') as mappings_file:
                mappings_configuration = json.load(mappings_file)
            logger.info("Mappings loaded successfully")
        except json.JSONDecodeError as json_error:
            logger.error(f"Invalid JSON in mappings: {json_error}")
            sys.exit(1)

        # Validate schema
        if not os.path.isfile(args.schema):
            logger.error(f"Schema file not found: {args.schema}")
            sys.exit(1)
        try:
            with open(args.schema, 'r', encoding='utf-8') as schema_file:
                schema_definition = json.load(schema_file)
            jsonschema.validate(instance=mappings_configuration, schema=schema_definition)
            logger.info("Mappings validated against schema")
        except ValidationError as validation_error:
            logger.error(f"Mappings validation failed: {validation_error.message}")
            sys.exit(1)

    # Conversion
    try:
        merged_config_lines = merge_config_files(
            args.input_file,
            included_files=set(),
            parser_directory=args.parser_dir,
            logger=logger
        )
        logger.info(f"Merged config: {len(merged_config_lines)} lines total")

        parsed_config_data = parse_config_lines(merged_config_lines, logger)
        apply_mappings_and_transformations(parsed_config_data, mappings_configuration, logger)
        yaml_structure, yaml_writer = build_yaml_structure(parsed_config_data, logger)

        with open(args.output_file, 'w', encoding='utf-8') as output_file:
            yaml_writer.dump(yaml_structure, output_file)

        logger.info(f"Conversion complete: {args.output_file}")

    except Exception as conversion_error:
        logger.exception(f"Conversion failed: {conversion_error}")
        sys.exit(1)


if __name__ == '__main__':
    main()