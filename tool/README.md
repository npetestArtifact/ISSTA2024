# NPETest

This directory contains source codes for "Effective Unit Test Generation for Java Null Pointer Exceptions" - NPETest.


# LICENSE
This work is funded from an IT company, and the license [LICENSE](./tool/LICENSE) is confirmed by the IT company.

## Table of Contents
- [Installation](#installation)
    * [Maven command](#maven-command)
    * [Build script](#build-script-recommended)
- [Usage](#usage)
    * [Input Format](#input-format)
        + [Maven project](#maven-project)
        + [JAR file](#jar-file)
    * [CUTs given with NPE Candidates](#cuts-given-with-npe-candidates)
    * [Output Files](#output-files)
        + [Test cases](#test-cases)
        + [Cache of Spoon Model](#cache-of-spoon-model)
        + [Other output files](#other-output-files)
    + [Additional Options](#additional-options)
## Installation
Requirements
- JDK 15
- Maven

#### Maven command
```shell
# export JAVA_HOME="path/to/jdk-15"
mvn clean package

alias npetest="java -jar /path/to/npetest-master-1.0-SNAPSHOT.jar"
```

If the building process fails due to testcases, please add the following option "-Dskiptests".

```
# export JAVA_HOME="path/to/jdk-15"
mvn clean package -DskipTests
```

#### Build script (recommended):
We provide python3 wrapper to easily run the tool, within `apps` directory.
After running `./apps/init.sh` and add the wrapper's path, you can use `npetest` command promptly. 
```shell
./apps/init.sh
export PATH="${PATH}:<npetest_home>/apps/bin"
```

## Usage

The tool takes three mandatory options:
- `--mvn|--jar`: A path to maven directory or jar archive file.  
- `--cuts`: A set of class under tests, separated by comma
- `--auxiliary-classpath`: A set of classpath, separated by colon, which target projects are depending on (e.g., `target/dependency/*.jar` for Maven project).
 
### Input Format
#### Maven project
```shell
npetest --mvn <path_to_mvn_project> --cuts="xxx.yyy.ClassA,aaa.bbb.ClassB" \
  --auxiliary-classpath="path/to/jar1.jar:path/to/jar2.jar:..."
```
For CUTs residing in Maven project, the project root directory and CUTs are passed.
In this case, the whole classes defined in Maven projects are referred during generating
testcases for CUTs.

#### JAR file
```shell
npetest --jar <path_to_jar_file> --cuts="xxx.yyy.ClassA,aaa.bbb.ClassB" \
  --auxiliary-classpath="path/to/jar1.jar:path/to/jar2.jar:..."
  --output-dir=<path_to_output_directory>
```
The usage example is similar to Maven case, except that the `--output-dir` option is required.
(For Maven input, it is automatically assigned with project directory.)

### Output Files
The overview of output files are like below. 
```text
output_dir
├── <output_dir>/npetest
│                   ├── xxx
│                   │    └── yyy
│                   │         ├── ClassA_NPETest_Seed.java
│                   │         └── ClassA_NPETest_Mutant.java
│                   └── ...
├── <output_dir>/npetest-report/
└── <output_dir>/npetest-debug/
```

#### Test cases
Test suites are constructed based on CUTs and their packages, and generated in `npe-tests`
directory. 

#### Files generated in debug mode
Files in `npetest-debug` directory are created when `--debug` option is passed.
They include error log during test case generation, due to the limited support the tool has, if any.

### Additional Options
- `--time-budget` - The time budget given as second (default: 120)

For further details, `npetest --help` will give some information. 
