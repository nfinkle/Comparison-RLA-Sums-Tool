all: compile
test: compile
	java VotingSheets TestInput.csv

clean:
	rm *.class
	rm -r 2018*

compile:
	javac *.java