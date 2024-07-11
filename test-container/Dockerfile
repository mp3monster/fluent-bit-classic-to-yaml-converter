from eclipse-temurin:21-jdk-alpine
LABEL maintainer "Phil Wilkins docker@mp3monster.org"
LABEL Description="Dynamic execution of the Fluent Bit Converter docker image" Vendor="mp3monster" Version="0.1"
ARG RELEASE=main
ARG GIT=false
ENV FLB_CONVERT_DEBUG=false
ENV FLB_REPORT_FILE=true
RUN mkdir ./test-src; mkdir ./test-comparison
COPY ./*.sh .
COPY --chmod=777  ./test/* ./test-src/
COPY --chmod=777 ./test-expected/* ./test-expected/
# We'll retrieve the utility from  GitHub, but overwrite it with the local one if it exists - this means new development will 
# get tested, but if run remotely as release test or auto triggered regression then we'll pick from GitHub
COPY  ./*.java .
RUN if [$GIT]; then wget https://github.com/mp3monster/fluent-bit-classic-to-yaml-converter/raw/${RELEASE}/src/FLBConverter/FLBConverter.java ; echo pulled from GIT; fi
# use the ls -al to see if there are differences in the version from GitHub and retrieved locally
RUN chmod a+rx *.sh
CMD source regression.sh