# fluent-bit-classic-to-yaml-converter
New implementation built with Python.  for the background on this implementation checkout []

## CLI

The CLI needs a number of configuration details, but has default values for many of them.

The tool can be simply run with the following command:

'''
py converter.py test.conf
'''
 Where _test.conf_ is the configuration file to be converted. Alternatively, if you want to see the command line options
 '''
 py converter.py -h
 '''
 
 Which will result in the help being displayed on the console.

There are several optional parameters, which are defaulted as follows:
* --output_file _OUTPUT_FILE_ this is the name (and optionally path) of the transformed output configuration.  If this isn't set, then it uses the same location and name of the input as its base name and appends a post fix of _.yaml_ to the file.
* --mappings _MAPPINGS_  - The mappings configuration file by default is called _mappings.json_ and colocated with the parser tool.
* --schema SCHEMA - the mappings file is controlled by its schema.  There should be no reason to modify this, and will pick up the schema from the same folder as the Python code.  But if you're paranoid, then you can replace with a visible central location.
* --parser-dir PARSER_DIR
* -v or -vv - as with Fluent Bit, this sets the logging level to Verbose or very verbose

## Installation
While we would prefer not to have any external dependencies, we have elected to use a couple so that we do not have to build our own YAML generator. The dependencies can seen in [requirements.txt] All the dependencies are available from PyPI.

### Isolate the tool from other code using a Virtual Environment
If you don't want this code and its dependencies to impact anything else on your machine then you need the commands:

'''
cd repo
# repo bering where you want the tool be used, and you've dowenloaded the code
pip install virtualenv
virtualenv venv
source venv/bin/activate
'''

There are of course other ways to achieve this such as the pipenv tool.

### Get dependencies

Simply use the pip command from the repo folder
'''
pip install -r requirements.txt
'''

## Mapping Configuration File



## Testing

We've included a set of configs that we have used for helping to test the tool in the /test folder.  It isn't exhaustive, but covers a range of scenarios that could confuse the tools such as 
* includes
* separate parser files with regular expressions
* Env variable references.
