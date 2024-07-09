from eclipse-temurin:21-jdk-alpine
LABEL maintainer "Phil Wilkins docker@mp3monster.org"
LABEL Description="Dynamic execution of the Fluent Bit Converter docker image" Vendor="mp3monster" Version="0.1"
# VOLUME ["/vol/conf"]
ARG RELEASE=main
#ENV FLB_PATH_PREFIX=/vol/conf/
ENV FLB_CONVERT_DEBUG=false
ENV FLB_REPORT_FILE=true
RUN mkdir ./test-src; mkdir ./test-comparison
COPY ./*.sh .
COPY --chmod=777  ./test/* ./test-src/
COPY ./test-expected/* ./test-expected/
RUN wget https://github.com/mp3monster/fluent-bit-classic-to-yaml-converter/raw/${RELEASE}/src/FLBConverter/FLBConverter.java
RUN chmod a+rx *.sh
CMD source regression.sh
#CMD ls -al ./test-src; ls -al ./test-comparison