"""
Fluent Bit Classic Config to YAML Converter
Features:
  - @INCLUDE, @PARSER_FILE, Lua scripts
  - Key/value mapping & transformations
  - External parser files
  - JSON schema validation
  - Full debug logging via Python logging
  - Support for 'pipeline' nesting in YAML output
  - Support for top-level 'env' from @SET
  - Support for top-level 'upstream_servers' from [UPSTREAM]
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
    logger = logging.getLogger("fluentbit_converter")
    logger.setLevel(logging.DEBUG)  # Always capture all

    # Console handler
    console_handler = logging.StreamHandler(sys.stderr)
    level = {0: logging.WARNING, 1: logging.INFO, 2: logging.DEBUG}.get(
        verbosity, logging.DEBUG
    )
    console_handler.setLevel(level)

    # Formatter
    formatter = logging.Formatter(
        fmt="%(asctime)s [%(levelname)s] %(message)s", datefmt="%H:%M:%S"
    )
    console_handler.setFormatter(formatter)

    # Avoid duplicate handlers
    if not logger.handlers:
        logger.addHandler(console_handler)

    logger.debug(f"Logging setup with verbosity level: {verbosity}")
    return logger


# === Constants ===
SIZE_UNIT_CONVERSION = {
    "kb": 1024,
    "mb": 1024**2,
    "gb": 1024**3,
    "tb": 1024**4,
    "k": 1000,
    "m": 1000**2,
    "g": 1000**3,
    "t": 1000**4,
}

SECTION_MAPPING = {
    "input": "inputs",
    "filter": "filters",
    "output": "outputs",
    "parser": "parsers",
    "multiline_parser": "multiline_parsers",
    "upstream": "upstream_servers",
}

ORDERED_TOP_LEVEL_KEYS = ["env", "service", "pipeline", "upstream_servers"]

PIPELINE_ORDERED_KEYS = ["inputs", "filters", "parsers", "multiline_parsers", "outputs"]


# === Utility Functions ===
def convert_size_to_bytes(size_string: str, logger: logging.Logger) -> str:
    """Convert human-readable size to bytes."""
    logger.debug(f"Entering convert_size_to_bytes with size_string: {size_string}")
    normalized = size_string.strip().lower()
    for unit, multiplier in SIZE_UNIT_CONVERSION.items():
        logger.debug(f"Checking unit: {unit}")
        if normalized.endswith(unit):
            numeric_part = normalized[: -len(unit)]
            if numeric_part.replace(".", "").isdigit():
                result = str(int(float(numeric_part) * multiplier))
                logger.debug(f"Converted to {result} bytes")
                return result
    logger.debug(f"No unit match, returning original: {size_string}")
    return size_string


def apply_single_transform(
    value: str, transform_rule: Dict[str, Any], logger: logging.Logger
) -> str:
    """Apply one transformation rule."""
    logger.debug(
        f"Entering apply_single_transform with value: {value}, rule: {transform_rule}"
    )
    if not isinstance(value, str):
        logger.debug("Value is not string, returning as is")
        return value

    transform_type = transform_rule.get("type")
    logger.debug(f"Applying transform type: {transform_type}")

    try:
        transform_handlers = {
            "replace": lambda: value.replace(
                transform_rule["from"], transform_rule["to"]
            ),
            "regex_replace": lambda: re.compile(transform_rule["pattern"]).sub(
                transform_rule["replace"], value
            ),
            "convert_size": lambda: convert_size_to_bytes(value, logger),
            "bool_map": lambda: (
                transform_rule["true"]
                if value.strip().lower() in ["on", "true", "1", "yes"]
                else transform_rule["false"]
            ),
        }
        handler = transform_handlers.get(transform_type)
        if handler:
            result = handler()
            logger.debug(f"Transform result: {result}")
            return result
        else:
            logger.warning(f"Unknown transform type: {transform_type}")
    except Exception as transform_error:
        logger.warning(f"Transform failed: {transform_error}")
    logger.debug("Returning original value after failure")
    return value


# === Config Merging ===
def merge_config_files(
    config_path: str,
    included_files: Set[str],
    parser_directory: Optional[str],
    logger: logging.Logger,
) -> List[str]:
    """Recursively merge @INCLUDE and @PARSER_FILE."""
    logger.debug(f"Entering merge_config_files with config_path: {config_path}")
    absolute_path = os.path.abspath(config_path)
    logger.debug(f"Absolute path: {absolute_path}")

    if absolute_path in included_files:
        logger.debug("Cycle detected")
        raise ValueError(f"Cycle detected in includes: {absolute_path}")
    included_files.add(absolute_path)
    logger.debug(f"Added to included_files: {absolute_path}")

    try:
        with open(absolute_path, "r", encoding="utf-8") as file_handle:
            file_lines = file_handle.readlines()
        logger.debug(f"Read {len(file_lines)} lines from {config_path}")
    except FileNotFoundError:
        logger.error(f"File not found: {config_path}")
        raise FileNotFoundError(f"Config file not found: {config_path}")
    except Exception as read_error:
        logger.error(f"Read error: {read_error}")
        raise RuntimeError(f"Failed to read {config_path}: {read_error}")

    merged_lines = []
    current_directory = os.path.dirname(absolute_path)
    logger.debug(f"Current directory: {current_directory}")

    for line_index, line in enumerate(file_lines):
        stripped_line = line.strip()
        logger.debug(f"Processing line {line_index + 1}: {stripped_line or '<empty>'}")

        if stripped_line.startswith("@INCLUDE"):
            merged_lines.extend(
                handle_include(
                    stripped_line,
                    current_directory,
                    included_files,
                    parser_directory,
                    logger,
                )
            )
        elif stripped_line.startswith("@PARSER_FILE") and parser_directory:
            merged_lines.extend(
                handle_parser_file(
                    stripped_line, parser_directory, current_directory, logger
                )
            )
        else:
            merged_lines.append(line)

    logger.debug(f"Returning merged_lines with length: {len(merged_lines)}")
    return merged_lines


def handle_include(
    stripped_line: str,
    current_directory: str,
    included_files: Set[str],
    parser_directory: Optional[str],
    logger: logging.Logger,
) -> List[str]:
    logger.debug(f"Handling @INCLUDE: {stripped_line}")
    include_pattern = stripped_line[9:].strip().strip("'\"")
    matched_paths = sorted(glob.glob(os.path.join(current_directory, include_pattern)))
    logger.debug(f"@INCLUDE pattern: {include_pattern} -> {len(matched_paths)} files")

    if not matched_paths:
        logger.warning(f"@INCLUDE not found: {include_pattern}")
        return [f"# @INCLUDE {include_pattern}  # NOT FOUND\n"]

    sub_merged = []
    for included_path in matched_paths:
        relative_path = os.path.relpath(included_path, current_directory)
        logger.info(f"Including: {relative_path}")
        try:
            sub_lines = merge_config_files(
                included_path, included_files.copy(), parser_directory, logger
            )
            sub_merged.append(f"# Included from {relative_path}\n")
            sub_merged.extend(sub_lines)
        except Exception as include_error:
            logger.error(f"Failed to include {included_path}: {include_error}")
            sub_merged.append(
                f"# FAILED to include: {relative_path}  # {include_error}\n"
            )
    logger.debug(f"Handled include, sub_merged length: {len(sub_merged)}")
    return sub_merged


def handle_parser_file(
    stripped_line: str,
    parser_directory: str,
    current_directory: str,
    logger: logging.Logger,
) -> List[str]:
    logger.debug(f"Handling @PARSER_FILE: {stripped_line}")
    parser_filename = stripped_line[13:].strip().strip("'\"")
    parser_full_path = os.path.join(parser_directory, parser_filename)
    logger.debug(f"@PARSER_FILE: {parser_filename} -> {parser_full_path}")

    try:
        with open(parser_full_path, "r", encoding="utf-8") as parser_file:
            parser_lines = parser_file.readlines()
        relative_parser_path = os.path.relpath(parser_full_path, current_directory)
        logger.info(
            f"Loaded parser: {relative_parser_path} ({len(parser_lines)} lines)"
        )
        return [f"# Parser from {relative_parser_path}\n"] + parser_lines
    except Exception as parser_error:
        logger.error(f"Failed to load parser {parser_filename}: {parser_error}")
        return [f"# FAILED parser: {parser_filename}\n"]


# === Parsing ===
def parse_config_lines(lines: List[str], logger: logging.Logger) -> Dict[str, Any]:
    """Parse merged lines into structured config."""
    logger.debug(f"Entering parse_config_lines with {len(lines)} lines")
    logger.info(f"Parsing {len(lines)} merged lines")
    config_data = {}
    pending_comments = []
    current_section_dict = None
    current_section_type = None

    line_index = 0
    while line_index < len(lines):
        current_line = lines[line_index]
        line_content = current_line.rstrip("\n")
        stripped_content = line_content.strip()

        if not stripped_content:
            logger.debug(f"Skipping blank line at index {line_index}")
            line_index += 1
            continue

        if stripped_content.startswith("#"):
            pending_comments.append(stripped_content[1:].strip())
            logger.debug(f"Added comment: {stripped_content[1:].strip()}")
            line_index += 1
            continue

        # Handle @SET for env
        if stripped_content.startswith("@SET"):
            line_index = handle_set_env(
                stripped_content, config_data, logger, line_index
            )
            continue

        if stripped_content.startswith("[") and stripped_content.endswith("]"):
            line_index = handle_section_header(
                stripped_content, config_data, pending_comments, logger, line_index
            )
            pending_comments = []
            continue

        if current_section_dict is not None:
            line_index = process_section_content(
                line_content,
                current_section_dict,
                current_section_type,
                lines,
                logger,
                line_index,
            )
        else:
            logger.warning(f"Unhandled line before any section: {stripped_content}")
            line_index += 1

    logger.info(f"Parsed config structure: {list(config_data.keys())}")
    logger.debug(f"Exiting parse_config_lines")
    return config_data


def handle_set_env(
    stripped_content: str,
    config_data: Dict[str, Any],
    logger: logging.Logger,
    line_index: int,
) -> int:
    logger.debug(f"Handling @SET: {stripped_content}")
    set_parts = stripped_content[5:].strip().split("=", 1)
    if len(set_parts) == 2:
        name, value = set_parts
        config_data.setdefault("env", [])
        config_data["env"].append({"name": name.strip(), "value": value.strip()})
        logger.debug(f"Added env var: {name.strip()} = {value.strip()}")
    else:
        logger.warning(f"Invalid @SET: {stripped_content}")
    return line_index + 1


def handle_section_header(
    stripped_content: str,
    config_data: Dict[str, Any],
    pending_comments: List[str],
    logger: logging.Logger,
    line_index: int,
) -> int:
    logger.debug(f"Handling section header: {stripped_content}")
    section_name = stripped_content[1:-1].strip().lower()
    logger.debug(f"Found section: [{section_name.upper()}]")

    current_section_dict = {}
    if section_name == "service":
        config_data["service"] = current_section_dict
        current_section_type = "service"
    else:
        mapped_section = SECTION_MAPPING.get(section_name, section_name + "s")
        config_data.setdefault(mapped_section, [])
        config_data[mapped_section].append(current_section_dict)
        current_section_type = mapped_section

    if pending_comments:
        current_section_dict["_comment"] = "\n".join(pending_comments)
        logger.debug(f"Attached {len(pending_comments)} comment(s) to section")

    return line_index + 1


def process_section_content(
    line_content: str,
    current_section_dict: Dict[str, Any],
    current_section_type: str,
    lines: List[str],
    logger: logging.Logger,
    line_index: int,
) -> int:
    logger.debug(f"Processing section content: {line_content}")
    lua_match = re.match(r"^\s*(script|call)\s+(.+)", line_content, re.I)
    if lua_match:
        return handle_lua_block(
            lua_match, current_section_dict, lines, logger, line_index
        )

    key_value_parts = line_content.lstrip().split(maxsplit=1)
    if len(key_value_parts) == 2:
        config_key = key_value_parts[0].lower()
        config_value = key_value_parts[1].strip()

        if current_section_type == "upstream_servers" and config_key == "node":
            handle_upstream_node(current_section_dict, config_value, logger)
        else:
            current_section_dict[config_key] = config_value
            logger.debug(f"Key-value: {config_key} = {config_value}")
    else:
        logger.warning(f"Invalid line in section: {line_content.strip()}")

    return line_index + 1


def handle_lua_block(
    lua_match: re.Match,
    current_section_dict: Dict[str, Any],
    lines: List[str],
    logger: logging.Logger,
    line_index: int,
) -> int:
    logger.debug(f"Handling Lua block")
    key_name = lua_match.group(1).lower()
    value_start = lua_match.group(2)
    script_lines = [value_start]
    line_index += 1

    logger.debug(f"Found multi-line {key_name}: starting with '{value_start}'")

    while line_index < len(lines):
        next_line = lines[line_index]
        next_stripped = next_line.strip()
        if (
            not next_stripped
            or next_stripped.startswith("[")
            or re.match(r"^\s*(name|match|script|call)\s+", next_line, re.I)
        ):
            break
        script_lines.append(next_line.rstrip("\n"))
        line_index += 1

    full_script = "\n".join(script_lines)
    current_section_dict[key_name] = full_script
    current_section_dict[f"{key_name}_block"] = True
    logger.debug(f"Collected {len(script_lines)} lines for {key_name}")
    return line_index


def handle_upstream_node(
    current_section_dict: Dict[str, Any], config_value: str, logger: logging.Logger
) -> None:
    logger.debug(f"Handling upstream node: {config_value}")
    current_section_dict.setdefault("nodes", [])
    node_parts = config_value.split()
    if len(node_parts) < 3:
        logger.warning(f"Invalid node line: {config_value}")
    else:
        node_dict = {
            "name": node_parts[0],
            "host": node_parts[1],
            "port": node_parts[2],
        }
        if len(node_parts) > 3:
            node_dict["weight"] = node_parts[3]
        if len(node_parts) > 4:
            node_dict["tls"] = node_parts[4]
        current_section_dict["nodes"].append(node_dict)
        logger.debug(f"Added node: {node_dict}")


# === Mapping & Transform ===
def apply_mappings_and_transformations(
    config_data: Dict[str, Any], mappings_config: Dict[str, Any], logger: logging.Logger
) -> None:
    """Apply key renaming and value transforms."""
    logger.debug(f"Entering apply_mappings_and_transformations")
    mappings_root = mappings_config.get("mappings", {})
    logger.info("Applying mappings and transformations")

    # Service
    if "service" in config_data:
        apply_service_mappings(
            config_data["service"], mappings_root.get("service", {}), logger
        )

    # List sections (pipeline-related)
    for section_name in PIPELINE_ORDERED_KEYS:
        if section_name not in config_data:
            continue

        apply_section_mappings(
            section_name,
            config_data[section_name],
            mappings_root.get(section_name, {}),
            logger,
        )
    logger.debug(f"Exiting apply_mappings_and_transformations")


def apply_section_mappings(
    section_name: str,
    section_items: List[Dict[str, Any]],
    section_mappings: Dict[str, Any],
    logger: logging.Logger,
) -> None:
    logger.debug(f"Applying mappings for section: {section_name}")
    for section_item in section_items:
        plugin_name = section_item.get("name", "").lower()
        plugin_config = section_mappings.get(plugin_name, {})
        key_map = plugin_config.get("key_mappings", {})

        for old_key in list(section_item):
            if old_key in key_map and old_key != "_comment":
                new_key = key_map[old_key]
                section_item[new_key] = section_item.pop(old_key)
                if f"{old_key}_block" in section_item:
                    section_item[f"{new_key}_block"] = section_item.pop(
                        f"{old_key}_block"
                    )
                logger.debug(f"{section_name}.{plugin_name}: {old_key} -> {new_key}")

        for target_key, transform_list in plugin_config.get(
            "value_transformations", {}
        ).items():
            if target_key in section_item and not section_item.get(
                f"{target_key}_block"
            ):
                for transform_rule in transform_list:
                    old_value = section_item[target_key]
                    section_item[target_key] = apply_single_transform(
                        old_value, transform_rule, logger
                    )
                    logger.debug(
                        f"{section_name}.{plugin_name}: {target_key} '{old_value}' -> '{section_item[target_key]}'"
                    )


def apply_service_mappings(
    service_item: Dict[str, Any],
    service_mappings: Dict[str, Any],
    logger: logging.Logger,
) -> None:
    logger.debug("Applying service mappings")
    key_map = service_mappings.get("key_mappings", {})

    for old_key in list(service_item):
        if old_key in key_map and old_key != "_comment":
            new_key = key_map[old_key]
            service_item[new_key] = service_item.pop(old_key)
            if f"{old_key}_block" in service_item:
                service_item[f"{new_key}_block"] = service_item.pop(f"{old_key}_block")
            logger.debug(f"Service key: {old_key} -> {new_key}")

    for target_key, transform_list in service_mappings.get(
        "value_transformations", {}
    ).items():
        if target_key in service_item and not service_item.get(f"{target_key}_block"):
            for transform_rule in transform_list:
                old_value = service_item[target_key]
                service_item[target_key] = apply_single_transform(
                    old_value, transform_rule, logger
                )
                logger.debug(
                    f"Service transform: {target_key} '{old_value}' -> '{service_item[target_key]}'"
                )


# === YAML Output ===
def build_yaml_structure(config_data: Dict[str, Any], logger: logging.Logger):
    """Build final YAML structure with 'pipeline' nesting."""
    logger.debug("Entering build_yaml_structure")
    logger.info("Building YAML output structure")
    yaml_instance = YAML()
    yaml_instance.indent(mapping=2, sequence=4, offset=2)
    root_map = CommentedMap()

    top_level_handlers = {
        "pipeline": lambda data, log: build_pipeline_map(data, log),
        "env": lambda data, log: build_env_sequence(data.get("env", []), log),
        "service": lambda data, log: build_service_map(data.get("service", {}), log),
        "upstream_servers": lambda data, log: build_upstream_sequence(
            data.get("upstream_servers", []), log
        ),
    }

    for top_level_key in ORDERED_TOP_LEVEL_KEYS:
        handler = top_level_handlers.get(top_level_key)
        if handler:
            result = handler(config_data, logger)
            if result:
                root_map[top_level_key] = result

    # Add any extra top-level keys
    for key in config_data:
        if key not in ORDERED_TOP_LEVEL_KEYS and key not in PIPELINE_ORDERED_KEYS:
            root_map[key] = config_data[key]

    logger.debug("Exiting build_yaml_structure")
    return root_map, yaml_instance


def build_pipeline_map(
    config_data: Dict[str, Any], logger: logging.Logger
) -> Optional[CommentedMap]:
    logger.debug("Building pipeline map")
    pipeline_map = CommentedMap()
    for pipeline_key in PIPELINE_ORDERED_KEYS:
        if pipeline_key not in config_data:
            continue
        sequence = build_item_sequence(config_data[pipeline_key], logger, pipeline_key)
        pipeline_map[pipeline_key] = sequence
    if pipeline_map:
        logger.debug("Added 'pipeline' section")
        return pipeline_map
    return None


def build_env_sequence(
    env_data: List[Dict[str, str]], logger: logging.Logger
) -> Optional[CommentedSeq]:
    logger.debug("Building env sequence")
    if not env_data:
        return None
    env_sequence = CommentedSeq()
    for env_item in env_data:
        env_sequence.append(CommentedMap(env_item))
    logger.debug(f"Added {len(env_data)} env variables")
    return env_sequence


def build_service_map(
    service_data: Dict[str, Any], logger: logging.Logger
) -> Optional[CommentedMap]:
    logger.debug("Building service map")
    if not service_data:
        return None
    service_map = CommentedMap()
    for key, value in service_data.items():
        if service_data.get(f"{key}_block"):
            service_map[key] = LiteralScalarString(value + "\n")
            logger.debug(f"Service block: {key} (Lua/script)")
        else:
            service_map[key] = value
    if "_comment" in service_data:
        comment_text = "# " + service_data["_comment"].replace("\n", "\n# ") + "\n"
        comment_token = CommentToken(comment_text, CommentMark(0))
        service_map.ca.comment = [comment_token, None]
    return service_map


def build_upstream_sequence(
    upstream_data: List[Dict[str, Any]], logger: logging.Logger
) -> Optional[CommentedSeq]:
    logger.debug("Building upstream sequence")
    if not upstream_data:
        return None
    upstream_sequence = CommentedSeq()
    for upstream_index, upstream_item in enumerate(upstream_data):
        upstream_map = CommentedMap(
            {k: v for k, v in upstream_item.items() if k != "nodes"}
        )
        if "nodes" in upstream_item:
            nodes_sequence = CommentedSeq()
            for node in upstream_item["nodes"]:
                nodes_sequence.append(CommentedMap(node))
            upstream_map["nodes"] = nodes_sequence
        if "_comment" in upstream_item:
            comment_text = "# " + upstream_item["_comment"].replace("\n", "\n# ") + "\n"
            comment_token = CommentToken(comment_text, CommentMark(0))
            if upstream_index == 0:
                upstream_sequence.yaml_set_start_comment(comment_token)
            else:
                upstream_sequence.yaml_set_comment_before_after_key(
                    upstream_index, before=comment_token
                )
        upstream_sequence.append(upstream_map)
    logger.debug(f"Added {len(upstream_data)} upstream servers")
    return upstream_sequence


def build_item_sequence(
    items: List[Dict[str, Any]], logger: logging.Logger, section_key: str
) -> CommentedSeq:
    logger.debug(f"Building item sequence for {section_key}")
    sequence = CommentedSeq()
    for item_index, item in enumerate(items):
        item_map = CommentedMap()
        for key, value in item.items():
            if item.get(f"{key}_block"):
                item_map[key] = LiteralScalarString(value + "\n")
                logger.debug(f"{section_key}[{item_index}].{key}: block scalar")
            else:
                item_map[key] = value
        if "_comment" in item:
            comment_text = "# " + item["_comment"].replace("\n", "\n# ") + "\n"
            comment_token = CommentToken(comment_text, CommentMark(0))
            if item_index == 0:
                sequence.yaml_set_start_comment(comment_token)
            else:
                sequence.yaml_set_comment_before_after_key(
                    item_index, before=comment_token
                )
        sequence.append(item_map)
    return sequence


# === Main ===
def main() -> None:
    logger = logging.getLogger("fluentbit_converter")  # For main, but setup later
    parser = argparse.ArgumentParser(
        description="Convert classic Fluent Bit config to modern YAML format.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("input_file", help="Path to main Fluent Bit .conf file")
    parser.add_argument("--output_file", help="Output YAML file path")
    parser.add_argument(
        "--mappings", help="JSON mappings configuration file", default="mappings.json"
    )
    parser.add_argument(
        "--schema", help="JSON schema for mappings", default="mappings_schema.json"
    )
    parser.add_argument(
        "--parser-dir", help="Directory containing external .parser files", default="./"
    )
    parser.add_argument(
        "-v",
        "--verbose",
        action="count",
        default=0,
        help="Increase verbosity (-v, -vv)",
    )
    args = parser.parse_args()

    # Setup logging
    logger = setup_logging(min(args.verbose, 2))
    logger.debug("Entering main")
    logger.info("Starting Fluent Bit config conversion")

    # Validate input
    if not os.path.isfile(args.input_file):
        logger.error(f"Input file not found: {args.input_file}")
        sys.exit(1)

    if not args.output_file or len(args.output_file) == 0:
        args.output_file = args.input_file + ".yaml"
        logger.info(f"Defaulting the output file to {args.output_file}")

    # Load mappings
    mappings_configuration = {"mappings": {}}
    if args.mappings:
        logger.info(f"Loading mappings from: {args.mappings}")
        if not os.path.isfile(args.mappings):
            logger.error(f"Mappings file not found: {args.mappings}")
            sys.exit(1)
        try:
            with open(args.mappings, "r", encoding="utf-8") as mappings_file:
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
            with open(args.schema, "r", encoding="utf-8") as schema_file:
                schema_definition = json.load(schema_file)
            jsonschema.validate(
                instance=mappings_configuration, schema=schema_definition
            )
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
            logger=logger,
        )
        logger.info(f"Merged config: {len(merged_config_lines)} lines total")

        parsed_config_data = parse_config_lines(merged_config_lines, logger)
        apply_mappings_and_transformations(
            parsed_config_data, mappings_configuration, logger
        )
        yaml_structure, yaml_writer = build_yaml_structure(parsed_config_data, logger)

        with open(args.output_file, "w", encoding="utf-8") as output_file:
            yaml_writer.dump(yaml_structure, output_file)

        logger.info(f"Conversion complete: {args.output_file}")

    except Exception as conversion_error:
        logger.exception(f"Conversion failed: {conversion_error}")
        sys.exit(1)

    logger.debug("Exiting main")


if __name__ == "__main__":
    main()
