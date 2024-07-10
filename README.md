# fluent-bit-classic-to-yaml-converter

A simple Utility for converting Fluent Bit classic format configuration files to the new YAML format.



## Background

Fluent Bit's classic format configuration option does not support all new features. As a result, the YAML format needs to be adopted to take advantage of features like the new processor.  Converting the classic format to YAML can be tedious, and with YAML so sensitive to indentation, it is easy to make a small mistake.

This is why we have started building this utility. 

#### How it works

The utility is written in Java as a single-file package, so there is no need to use Maven or Gradle to build a jar—that process of converting code to jar happens in the run phase, making it convenient to run.

The utility reads the classic file line by line and maps each line into a structure to hold the various constructs in memory. Then, it loops through each type of construct (service, inputs, filters, and outputs), generating the YAML. 

 As it performs this process, it does address some of the quirks the ones currently address are:

- Wild card match declarations need the asterisk quoting. 
- Dummy attribute strings are wrapped with single quotes

#### Gaps & features (to be addressed)

Additions to be developed for the longer term:

- Extend the regression pack to have more tests.
- Add the ability to reference different locations for the `conversion.list` file.

#### Differences unlikely to be addressed

The definition of environment variables has not yet been addressed, and it could be problematic to try to do so.

## Running the Tool

The tool can be run in several ways -  for single-file conversions or multi-file conversions,

#### Single File Conversion 

The file to convert can be identified by either:

- providing a command line attribute with the file to convert
- Setting an environment variable called `FLBClassicFN`, which has a value for the file location.

The run command for this is:

```bash
`java FLBConverter.java`
```

 or

```bash
 `java FLBConverter.java ./test.conf`
```



#### Multi File Conversion

Multiple files can be converted in a single run by providing in the local folder to the utility a file called `conversion.list`

The file then contains the name of the files to be converted. One file per line in the `conversion.list` file.

The run command for this is:

`java FLBConverter.java`

#### Docker Container

The tool can be run in a Docker container, and the Docker file can be retrieved from [here](https://github.com/mp3monster/fluent-bit-classic-to-yaml-converter/blob/main/container/Dockerfile). You will need to build the image. Before doing that, it is worth noting that we can stipulate a specific release of the utility code to use by setting the argument RELEASE to be a release branch; otherwise, the container will pull the latest (main) version of the code. 

The container configuration expects the files to be mounted to /vol/conf.  The utility includes a feature that will offset the path, so if you use the `conversion.list` file (see above), then the path provided does not need to be modified to take this into account.

Command to build the Docker image (from the folder containing the docker file) with the latest features would be:

```bash
docker build . --no-cache -t flb-converter
```

We recommend using --no-cache as the container builds by pulling the code base, switching off the cache ensures the latest code is always pulled.

To build against a specific release e.g 0.2 (the earliest version we'd recommend using in the container) the command becomes:

```bash
docker build . --no-cache --build-arg="RELEASE=0.2" -t flb-converter
```

By default, the logging is at the Info level, so the console output will be reflected in the `.report` file generated.

To run the container, we can then use the command:
```bash
docker run -t flb-converter
```

or to direct the container to a different folder for the files, and clean up ...

```bash
docker run -v ./../test:/vol/conf --rm -t flb-converter
```

We've included in the repository a simple `build.sh.bat` (change the file extension for your environment) and a `run.[sh|bat]` to run the utility with our example test file. Here we've provided separate files to accommodate the pathing differences on Linux and Windows.

####  Output Filename

The generated file is written to the same folder as the source file (assuming permissions are ok) with the file extension changed to `.yaml`

#### Logging

To keep things very simple, logging is controlled directly to stdout. However, we can control whether the console output includes debug messages by setting an environment variable of `FLB_CONVERT_DEBUG` with a value of `true`.

#### Reporting Logged Details to File

We can ask the converter to write the output information to a file (the output filename with a postfix of `.report`). This can be enabled with the environment variable `FLB_REPORT_FILE` set to a value of `true` .

#### CLI Help

The tool supports the use of `--help` to get the help details.

# Configuration Summary

#### Environment Variable Controls

| Configuration (Environment Variables) | Description                                                  |
| ------------------------------------- | ------------------------------------------------------------ |
| `FLB_REPORT_FILE`                     | When set to `true` will generate additional file containing the details of the conversion, and any issues identified. |
| `FLB_CONVERT_DEBUG`                   | Switches on the debug level logging when set to `true`       |
| `FLB_PATH_PREFIX`                     | If you want to run the logic from another folder to that containing the configuration files and `conversion.list` we can apply a prefix which will be incorporated into the path e.g. `/vol/conf/` |
| `FLBClassicFN`                        | An environment variable approach to specifying a single file to convert. |
| `FLB_IDIOMATICFORM`                   | When set to true the Kubernetes idiomatic form is adopted for the attribute names e.g. `aMetric` rather than `a_metric` |

#### Command line options:

| CLI Value  | Description                       |
| ---------- | --------------------------------- |
| `--help`   | Outputs the help information      |
| <filename> | Name of the file to be processed. |



# Other Info

#### JavaDoc

For those interested in seeing the implementation details, we have generated JavaDoc for everything in the utility - makes for slightly easier reading. With the JavaDoc we've created a script file so you can see exactly how we've generated the doc and repeat the process.

Regression Tests

In the test-container folder is a alternate Docker image configured to run regression testing on the tool. The folder contains two child folders:

- test - this holds the input test files
- -test0expected - this holds the expected results

These folders are copied into the container (so no need to mount external file systems. The regression is then executed by the regression.sh script.  This script follows the pattern of:

- set the internal environment for the test
- run the utility
- perform a diff between the generated file AND the expected result and write to a file
- If the diff has a result creating output - then send the file to console, and fail the test.
- if successful announce success, and list the internal folder contents.

## Supporting this project

To help improve the utility, if you encounter configuration scenarios that the tool doesn't correctly translate, please share them through GitHub. Likewise for feature requests.
