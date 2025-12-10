import configparser
import sys
from collections import defaultdict
import logging


def main():
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

    config_file = sys.argv[1]

    # Read the original lines
    with open(config_file, "r") as f:
        lines = f.readlines()

    # Preprocess lines to handle duplicate sections by renumbering them
    processed_lines = []
    section_headings = []
    section_counts = defaultdict(int)

    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            # Empty lines or comments: keep as is
            processed_lines.append(line)
        elif stripped.startswith("[") and stripped.endswith("]"):
            # Section headers: extract original name and make unique if duplicate
            section_name = stripped[1:-1].strip()
            section_headings.append(section_name)
            section_counts[section_name] += 1
            unique_name = section_name
            if section_counts[section_name] > 1:
                unique_name = f"{section_name}_{section_counts[section_name]}"
            processed_lines.append(f"[{unique_name}]\n")
        else:
            # Property lines: keep as is, since ConfigParser will handle with space delimiter
            processed_lines.append(line)

    # Create ConfigParser with space as delimiter
    config = configparser.ConfigParser(
        delimiters=(" ",),
        allow_no_value=True,
        comment_prefixes=("#",),
        inline_comment_prefixes=("#",),
        strict=False,
    )

    # Read the processed configuration
    try:
        config.read_string("".join(processed_lines))
    except configparser.Error as e:
        logger.error(f"Error reading config file: {e}")
        sys.exit(1)

    # Print each of the section headings using logger
    if section_headings:
        logger.info("Found the following section headings:")
        for heading in section_headings:
            logger.info(heading)
    else:
        logger.info("No section headings found in the configuration file.")


if __name__ == "__main__":
    main()
