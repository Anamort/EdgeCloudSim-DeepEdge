#!/bin/sh
rm -rf ../../bin
mkdir ../../bin
javac -classpath "../../lib/*" -sourcepath ../../src ../../src/edu/boun/edgecloudsim/applications/deepLearning/TrainingEdge.java -d ../../bin
