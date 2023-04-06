#!/bin/bash

mvn clean package -DskipTests
docker build -t yti-termed-api:latest .
