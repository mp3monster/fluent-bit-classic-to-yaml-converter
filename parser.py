import configparser
import sys
from collections import defaultdict
import logging


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
    section_headings = []
    section_counts = defaultdict(int)

    for current_line in config_lines:
        stripped_line = current_line.strip()
        if not stripped_line or stripped_line.startswith("#"):
            # Empty lines or comments: keep as is
            processed_lines.append(current_line)
        elif stripped_line.startswith("[") and stripped_line.endswith("]"):
            # Section headers: extract original name and make unique if duplicate
            section_name = stripped_line[1:-1].strip()
            section_headings.append(section_name)
            section_counts[section_name] += 1
            unique_name = section_name
            if section_counts[section_name] > 1:
                unique_name = f"{section_name}_{section_counts[section_name]}"
            processed_lines.append(f"[{unique_name}]\n")
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

    return config_parser, section_headings


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

    if len(sys.argv) != 2:
        logger.info("Usage: python configReader.py <fluent-bit-config-file>")
        sys.exit(1)

    config_file_path = sys.argv[1]

    try:
        config_parser, section_headings = load_config(config_file_path)
    except configparser.Error as error:
        logger.error(f"Error reading config file: {error}")
        sys.exit(1)
    except Exception as error:
        logger.error(f"Unexpected error: {error}")
        sys.exit(1)

    # Print each of the section headings using logger
    if section_headings:
        logger.info("Found the following section headings:")
        for current_heading in section_headings:
            logger.info(current_heading)
    else:
        logger.info("No section headings found in the configuration file.")

    # List key-value pairs for each unique section type
    unique_sections = sorted(set(section_headings))
    for unique_section in unique_sections:
        list_key_value_pairs(config_parser, unique_section, logger)


if __name__ == "__main__":
    main()
