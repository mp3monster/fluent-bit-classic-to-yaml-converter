py ../converter.py -vv --mappings ../mappings.json --schema ../mappings_schema.json ./config.conf
fluent-bit --dry-run -c ./config.conf.yaml