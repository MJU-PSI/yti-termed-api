#!/bin/bash

mvn clean package
docker build -t yti-termed-api:latest .
