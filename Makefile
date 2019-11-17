all: compile
run: compile
	java Sheets < DenverCVREdited.csv

clean:
	rm *.class
	rm -r 2018*

compile:
	javac *.java