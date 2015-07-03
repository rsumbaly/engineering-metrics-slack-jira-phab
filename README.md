# Phabricator reports

Simple web application using Play and Guice to gather statistics from phabricator. 

Given a list of phabricator user names, generates a report of where the reviews are landing. 

## To run
```
Set the configurations in `conf/application.conf`
> sbt
> run 
In browser - http://localhost:9000/phab?usernames=rsumbaly,blah&nWeeks=4
```
