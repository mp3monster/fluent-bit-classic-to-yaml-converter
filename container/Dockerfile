from eclipse-temurin:21-jdk-alpine
LABEL maintainer "Phil Wilkins docker@mp3monster.org"
LABEL Description="Dynamic execution of the Fluent Bit Converter docker image" Vendor="mp3monster" Version="0.1"
VOLUME ["/vol/conf"]
ARG RELEASE=main
ENV FLB_PATH_PREFIX=/vol/conf/
ENV FLB_CONVERT_DEBUG=false
RUN wget https://github.com/mp3monster/fluent-bit-classic-to-yaml-converter/raw/${RELEASE}/src/FLBConverter/FLBConverter.java
RUN chmod a+rx *.java
CMD java FLBConverter.java
