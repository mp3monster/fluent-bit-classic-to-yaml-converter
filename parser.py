import configparser
import sys
from collections import defaultdict
import logging
import json
import re


def load_config(config_file_path):
    """
    Load and parse a Fluent Bit classic configuration file.

    This function reads the configuration file, preprocesses it to handle
    duplicate sections by renumbering them, and creates a ConfigParser
    object configured to use space as the delimiter. It returns the
    ConfigParser object and a list of original section headings.

    Parameters:
    config_file_path (str): The path to the Fluent Bit configuration file.

    Returns:
    tuple: A tuple containing the ConfigParser object and a list of section headings.

    Raises:
    configparser.Error: If there is an error parsing the configuration.
    Exception: For any other unexpected errors during file reading or processing.
    """
    # Read the original lines
    with open(config_file_path, "r") as file_handle:
        config_lines = file_handle.readlines()

    # Preprocess lines to handle duplicate sections by renumbering them
    processed_lines = []
    sections_list = []
    section_counts = defaultdict(int)

    for current_line in config_lines:
        stripped_line = current_line.strip()
        if not stripped_line or stripped_line.startswith("#"):
            # Empty lines or comments: keep as is
            processed_lines.append(current_line)
        elif stripped_line.startswith("[") and stripped_line.endswith("]"):
            # Section headers: extract original name and make unique if duplicate
            section_name = stripped_line[1:-1].strip()
            section_counts[section_name] += 1
            unique_name = section_name
            if section_counts[section_name] > 1:
                unique_name = f"{section_name}_{section_counts[section_name]}"
            processed_lines.append(f"[{unique_name}]\n")
            sections_list.append((section_name, unique_name))
        else:
            # Property lines: keep as is, since ConfigParser will handle with space delimiter
            processed_lines.append(current_line)

    # Create ConfigParser with space as delimiter
    config_parser = configparser.ConfigParser(
        delimiters=(" ",),
        allow_no_value=True,
        comment_prefixes=("#",),
        inline_comment_prefixes=("#",),
        strict=False,
    )

    # Read the processed configuration
    config_parser.read_string("".join(processed_lines))

    return config_parser, sections_list


def load_mappings(mappings_file_path):
    """
    Load the mappings from a JSON file compliant with the provided schema.

    Parameters:
    mappings_file_path (str): The path to the mappings JSON file.

    Returns:
    dict: The mappings data structure.
    """
    with open(mappings_file_path, "r") as file_handle:
        data = json.load(file_handle)
    return data["mappings"]


def get_section_type(section_name):
    """
    Determine the section type key in the mappings based on the config section name.

    Parameters:
    section_name (str): The original section name from the config.

    Returns:
    str: The corresponding section type in the mappings schema.
    """
    upper = section_name.upper()
    if upper == "SERVICE":
        return "service"
    elif upper == "MULTILINE_PARSER":
        return "multiline_parsers"
    elif upper == "PARSER":
        return "parsers"
    else:
        return upper.lower() + "s"


def get_plugin_mappings(mappings, section_name, plugin_name):
    """
    Look up the mappings for a plugin based on section name and plugin name.

    Parameters:
    mappings (dict): The loaded mappings data.
    section_name (str): The original section name.
    plugin_name (str or None): The plugin name (from 'Name' property).

    Returns:
    dict: The plugin-specific mappings.
    """
    section_type = get_section_type(section_name)
    if section_type == "service":
        return mappings.get("service", {})
    else:
        return mappings.get(section_type, {}).get(
            plugin_name.lower() if plugin_name else "", {}
        )


def apply_transform(value, transforms):
    """
    Apply a sequence of transformations to a value.

    Parameters:
    value (str or None): The value to transform.
    transforms (list): List of transform dictionaries.

    Returns:
    str or None: The transformed value.
    """
    if value is None:
        return None
    for transform in transforms:
        transform_type = transform["type"]
        if transform_type == "replace":
            value = value.replace(transform["from"], transform["to"])
        elif transform_type == "regex_replace":
            value = re.sub(transform["pattern"], transform["replace"], value)
        elif transform_type == "convert_size":
            value = convert_size(value)
        elif transform_type == "bool_map":
            if value.lower() == transform["true"].lower():
                value = "true"
            elif value.lower() == transform["false"].lower():
                value = "false"
    return value


def convert_size(value):
    """
    Convert a human-readable size string to bytes as a string.

    Parameters:
    value (str): The size string to convert.

    Returns:
    str: The size in bytes or the original value if unparseable.
    """
    units = {
        "B": 1,
        "KB": 1024,
        "MB": 1024**2,
        "GB": 1024**3,
        "TB": 1024**4,
        "PB": 1024**5,
        "KIB": 1024,
        "MIB": 1024**2,
        "GIB": 1024**3,
        "TIB": 1024**4,
        "PIB": 1024**5,
    }
    match = re.match(r"^(\d+(?:\.\d+)?)\s*([a-zA-Z]+)?$", value.strip())
    if match:
        num = float(match.group(1))
        unit = match.group(2).upper() if match.group(2) else "B"
        multiplier = units.get(unit, 1)
        return str(int(num * multiplier))
    else:
        return value


def transform_section(properties_dict, plugin_mappings):
    """
    Transform a section's properties using the provided mappings.

    Applies value transformations first, then renames keys.

    Parameters:
    properties_dict (dict): The original properties {key: value}.
    plugin_mappings (dict): The mappings for the plugin.

    Returns:
    dict: The transformed properties.
    """
    key_mappings = plugin_mappings.get("key_mappings", {})
    value_transformations = plugin_mappings.get("value_transformations", {})
    new_dict = {}
    for old_key, value in properties_dict.items():
        transforms = value_transformations.get(old_key, [])
        transformed_value = apply_transform(value, transforms)
        new_key = key_mappings.get(old_key, old_key)
        new_dict[new_key] = transformed_value
    return new_dict


def list_key_value_pairs(config_parser, section_name, logger):
    """
    List key-value pairs for a given section name.

    This function iterates through the sections in the ConfigParser,
    finds those matching the given section_name (including renumbered
    duplicates), and logs the key-value pairs for each instance.

    Parameters:
    config_parser (configparser.ConfigParser): The parsed configuration object.
    section_name (str): The name of the section to list pairs for.
    logger (logging.Logger): The logger object for output.

    Returns:
    None
    """
    section_instance_count = 0
    for unique_section in config_parser.sections():
        if unique_section == section_name or unique_section.startswith(
            section_name + "_"
        ):
            section_instance_count += 1
            logger.info(
                f"Key-value pairs for section {section_name}"
                + (f" #{section_instance_count}" if section_instance_count > 1 else "")
                + ":"
            )
            section_options = config_parser.options(unique_section)
            if section_options:
                for current_option in section_options:
                    current_value = config_parser.get(unique_section, current_option)
                    if current_value is None:
                        logger.info(f"  Key: {current_option}, Value: None")
                    else:
                        logger.info(f"  Key: {current_option}, Value: {current_value}")
            else:
                logger.info("  No key-value pairs found.")


def main():
    """
    Main entry point for the configReader program.

    This function sets up logging, parses command-line arguments,
    loads the configuration file, prints section headings, and lists
    key-value pairs for each unique section.

    Exits with status 1 if usage is incorrect or errors occur.

    Returns:
    None
    """
    # Configure logging
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s - %(levelname)s - %(message)s",
        handlers=[logging.StreamHandler(sys.stdout)],
    )
    logger = logging.getLogger(__name__)

    if len(sys.argv) != 3:
        logger.info(
            "Usage: python configReader.py <fluent-bit-config-file> <mappings.json>"
        )
        sys.exit(1)

    config_file_path = sys.argv[1]
    mappings_file_path = sys.argv[2]

    try:
        config_parser, sections_list = load_config(config_file_path)
    except configparser.Error as error:
        logger.error(f"Error reading config file: {error}")
        sys.exit(1)
    except Exception as error:
        logger.error(f"Unexpected error: {error}")
        sys.exit(1)

    try:
        mappings = load_mappings(mappings_file_path)
    except Exception as error:
        logger.error(f"Error loading mappings file: {error}")
        sys.exit(1)

    # Derive section headings from sections_list
    section_headings = [current_heading for current_heading, _ in sections_list]

    # Print each of the section headings using logger
    if section_headings:
        logger.info("Found the following section headings:")
        for current_heading in section_headings:
            logger.info(current_heading)
    else:
        logger.info("No section headings found in the configuration file.")

    # List key-value pairs for each unique section type
    unique_sections = sorted(set([name for name, _ in sections_list]))
    for unique_section in unique_sections:
        list_key_value_pairs(config_parser, unique_section, logger)

    # Apply and list transformed sections
    logger.info("Transformed sections:")
    for original_name, unique_section in sections_list:
        # Get original properties as dict
        properties_dict = {}
        for key in config_parser.options(unique_section):
            value = config_parser.get(unique_section, key)
            properties_dict[key] = value

        # Determine plugin name if applicable
        plugin_name = None
        if original_name.upper() != "SERVICE":
            plugin_name = properties_dict.get("Name") or properties_dict.get("name")

        # Get mappings
        plugin_mappings = get_plugin_mappings(mappings, original_name, plugin_name)

        # Transform
        transformed_properties = transform_section(properties_dict, plugin_mappings)

        # Log transformed
        logger.info(
            f"Transformed key-value pairs for {original_name}"
            + (f" ({plugin_name})" if plugin_name else "")
            + ":"
        )
        if transformed_properties:
            for key, value in sorted(transformed_properties.items()):
                logger.info(f"  Key: {key}, Value: {value}")
        else:
            logger.info("  No properties.")


if __name__ == "__main__":
    main()
