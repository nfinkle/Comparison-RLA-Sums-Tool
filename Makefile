all: compile
test: compile
	java VotingSheets DenverCVREdited.csv

clean:
	rm *.class
	rm -r 2018*

compile:
	javac *.java