import os
import json
import argparse
import glob
from ruamel.yaml import YAML
from ruamel.yaml.comments import CommentedMap, CommentedSeq
from ruamel.yaml.tokens import CommentToken
from ruamel.yaml.parser import CommentMark
import jsonschema

def merge_configs(current_file, included=set()):
    full_path = os.path.abspath(current_file)
    if full_path in included:
        raise ValueError(f"Cycle detected in includes: {full_path}")
    included.add(full_path)
    with open(full_path, 'r') as f:
        lines = f.readlines()
    result = []
    current_dir = os.path.dirname(full_path)
    for line in lines:
        stripped = line.strip()
        if stripped.startswith('@INCLUDE'):
            include_spec = stripped[8:].strip() if ' ' in stripped else ''
            include_paths = sorted(glob.glob(os.path.join(current_dir, include_spec)))
            for inc_path in include_paths:
                rel_inc = os.path.relpath(inc_path, current_dir)
                sub_lines = merge_configs(inc_path, included.copy())
                result.append(f"# Included from {rel_inc}\n")
                result.extend(sub_lines)
        else:
            result.append(line)
    return result

def parse_merged(lines):
    data = {}
    pending_comments = []
    current_dict = None
    current_group_key = None
    section_map = {
        'input': 'inputs',
        'filter': 'filters',
        'output': 'outputs',
        'parser': 'parsers',
        'multiline_parser': 'multiline_parsers',
    }
    for line in lines:
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith('#'):
            pending_comments.append(stripped[1:].strip())
            continue
        if stripped.startswith('[') and stripped.endswith(']'):
            section_name = stripped[1:-1].strip().lower()
            if section_name == 'service':
                if 'service' not in data:
                    data['service'] = {}
                current_dict = data['service']
                current_group_key = 'service'
            else:
                group_key = section_map.get(section_name, section_name + 's')
                if group_key not in data:
                    data[group_key] = []
                current_dict = {}
                data[group_key].append(current_dict)
                current_group_key = group_key
            if pending_comments:
                current_dict['_comment'] = ' '.join(pending_comments)
                pending_comments = []
        elif current_dict is not None:
            parts = line.lstrip().split(maxsplit=1)
            if len(parts) == 2:
                key = parts[0].lower()
                value = parts[1].strip()
                current_dict[key] = value
    return data

def apply_mappings(data, mappings_config):
    # Apply to service
    if 'service' in data:
        key_maps = mappings_config.get('mappings', {}).get('service', {}).get('key_mappings', {})
        item = data['service']
        for old in list(item):
            if old in key_maps and old != '_comment':
                new = key_maps[old]
                item[new] = item.pop(old)
    # Apply to list-based sections
    for section in ['inputs', 'filters', 'outputs', 'parsers', 'multiline_parsers']:
        if section in data:
            sec_maps = mappings_config.get('mappings', {}).get(section, {})
            for item in data[section]:
                name = item.get('name', '').lower()
                key_maps = sec_maps.get(name, {}).get('key_mappings', {})
                for old in list(item):
                    if old in key_maps and old != '_comment':
                        new = key_maps[old]
                        item[new] = item.pop(old)

def build_yaml_data(data):
    yaml_data = CommentedMap()
    ordered_keys = ['service', 'inputs', 'filters', 'parsers', 'multiline_parsers', 'outputs']
    for key in ordered_keys:
        if key in data:
            if key == 'service':
                item = data[key]
                cm = CommentedMap({k: v for k, v in item.items() if k != '_comment'})
                yaml_data[key] = cm
                if '_comment' in item:
                    com = CommentToken(f"# {item['_comment']}\n", CommentMark(0))
                    yaml_data.ca.items[key] = [com, None, None, None]
            else:
                seq = CommentedSeq()
                for idx, item in enumerate(data[key]):
                    cm = CommentedMap({k: v for k, v in item.items() if k != '_comment'})
                    if '_comment' in item:
                        com = CommentToken(f"# {item['_comment']}\n", CommentMark(0))
                        if idx == 0:
                            if seq.ca.comment is None:
                                seq.ca.comment = [None, []]
                            seq.ca.comment[1].append(com)
                        else:
                            if idx - 1 not in seq.ca.items:
                                seq.ca.items[idx - 1] = [None, None, [], None]
                            seq.ca.items[idx - 1][2].append(com)
                    seq.append(cm)
                yaml_data[key] = seq
    # Add any other keys
    for key in data:
        if key not in ordered_keys:
            yaml_data[key] = data[key]  # simplistic, no comments
    return yaml_data

def main():
    parser = argparse.ArgumentParser(description='Convert classic Fluent Bit config to YAML.')
    parser.add_argument('input_file', help='Path to the classic Fluent Bit config file')
    parser.add_argument('output_file', help='Path to the output YAML file')
    parser.add_argument('--mappings', help='Path to the JSON mappings configuration file', default=None)
    parser.add_argument('--schema', help='Path to the JSON schema file', default='mappings_schema.json')
    args = parser.parse_args()

    if args.mappings:
        with open(args.mappings, 'r') as f:
            mappings_config = json.load(f)
        with open(args.schema, 'r') as f:
            schema = json.load(f)
        jsonschema.validate(instance=mappings_config, schema=schema)
    else:
        mappings_config = {}

    merged_lines = merge_configs(args.input_file)
    data = parse_merged(merged_lines)
    apply_mappings(data, mappings_config)
    yaml_data = build_yaml_data(data)
    yaml = YAML()
    yaml.indent(mapping=2, sequence=4, offset=2)
    with open(args.output_file, 'w') as f:
        yaml.dump(yaml_data, f)

if __name__ == '__main__':
    main()