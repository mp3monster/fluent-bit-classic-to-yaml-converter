# fluent-bit-classic-to-yaml-converter

A simple Utility for converting Fluent Bit classic format configuration files to the new YAML format.



## Background



Fluent Bit's classic format configuration option does not support all new features. As a result, the YAML format needs to be adopted to take advantage of features like the new processor.  Converting the classic format to YAML can be tedious, and with YAML so sensitive to indentation, it is easy to make a small mistake.

This is why we have started building this utility. 

#### How it works

The utility is written in Java as a single-file package, so there is no need to use Maven or Gradle to build a jar—that process of converting code to jar happens in the run phase, making it convenient to run.

The utility reads the classic file line by line and maps each line into a structure to hold the various constructs in memory. Then, it loops through each type of construct (service, inputs, filters, and outputs), generating the YAML. 

 As it performs this process, it does address some of the quirks the ones currently address are:

- wild card match declarations need the asterisk quoting. 
- Dummy attribute strings are wrapped with single quotes

#### Gaps & features (to be addressed <u>soon</u>)

There are some functional gaps/needs that need to be addressed, and the plan is to do so quickly:

- Docker container so the utility can be run without a local Java install
- Test configurations published
- Add Javadoc

#### Gaps & features (to be addressed)

Additions to be developed for the longer term:

- Regression test pack  (Docker image?)
- Write the output in the same folder as the source file

#### Differences unlikely to be addressed

There are a few differences that we're not planning on addressing - as there isn't a simple answer:

- Order sensitivity - solution use match declarations

## Running the Tool

The tool can be run in several ways -  for single-file conversions or multi-file conversions,

#### Single File Conversion 

We the file to convert can be identified by either:

- providing a command line attribute with the file to convert
- Setting an environment variable called `FLBClassicFN` which has a value of the file location.

The run command for this is:

`java FLBConverter.java` or `java FLBConverter.java ./test.conf`

#### Multi File Conversion

Multiple files can be converted in a single run by providing in the local folder to the utility a file called `conversion.list`

The file then contains the name of the files to be converted. Each file must appear on its own line.

The run command for this is:

`java FLBConverter.java`

####  Output Filename

The generated file at the moment is placed in the local filesystem.

#### Logging

To keep things very simple logging is controlled directly to std out. But we can control whether the console output includes debug messages.  This is done by setting an environment variable of `FLB_CONVERT_DEBUG` with the value `true`.

## Supporting this project

To help make the utility better if you encounter configuration scenarios that the tool doesn't correctly translate, then please share them.
