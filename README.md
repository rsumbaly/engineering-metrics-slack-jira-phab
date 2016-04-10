# Phabricator reports

Simple web application using Play and Guice to gather statistics from phabricator. 

Given a list of phabricator user names, generates a report of where the reviews are landing. 

## To run
```
Set the configurations in `conf/application.conf`
> sbt
> run 
In browser - http://localhost:9000/phab?usernames=user1,user2&nWeeks=4
```

## To deploy to remote box

### Manual
```
> sbt dist
Copy zip from `target/universal`
> ./bin/phabricator-report > new_log &
```

### Docker

Make sure you have `docker` and `aws` CLI installed
```
> sbt docker:publishLocal
> docker images
[Check if your image exists]
> sbt docker:staging
[To test the Docker file output in target]
>  docker run --name phab -p 8080:9000 phabricator-reports:0.0.2
```

# Slack 

Integration with slack to publish reports
a) Webhook
b) Bot

# JIRA

Steps related to => https://developer.atlassian.com/jiradev/jira-apis/jira-rest-apis/jira-rest-api-tutorials/jira-rest-api-example-oauth-authentication

a) Setup new application here - https://[company].atlassian.net/plugins/servlet/applinks/listApplicationLinks
- Use dummy consumer key and name, say 'test-report'. Copy public key from your machine. 

b) 
```
> sbt
[phabricator-report] runMain jira.JiraClientApp requestToken test-report [private key file path - corresponding to public key entered above] https://[company].atlassian.net http://[company].org
```
You will get the request ioken, request token secret and the ...
c)... authorization url - that you need to go to manually and accept. After you accept you will get the `oauth_verifier` which...
d) 
```
[phabricator-report] runMain jira.JiraClientApp accessToken test-report [private key file path - corresponding to public key entered above] https://[company].atlassian.net http://[company].org [requestToken] [requestTokenSecret] [oauthVerifier]
```
This will give you the access token that you save into `conf/prod.conf`
e) To revoke access at any given time go to = https://[company].atlassian.net/plugins/servlet/oauth/users/access-tokens

f) Check if everything works
```
[phabricator-report] runMain jira.JiraClient testGetRequest test-report [private key file path - corresponding to public key entered above] https://[company].atlassian.net http://[company].org [accessToken] [jiraUrl]
```
Example jiraUrl - https://[company].atlassian.net/rest/api/2/issue/[ticket]

# On AWS
> Use default Linux based AMI
> sudo yum remove java-1.7.0-openjdk
> sudo yum install java-1.8.0
