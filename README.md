# HTTP Request Echo App Engine Web App

This is a web app that helps you debug web tools by echoing the content of 
your HTTP requests right back to you. It is deployable on Google App Engine.

After you create a Google Cloud project through the developer console, take note
of the project ID, and you can deploy this app with some commands like this:

    $ git clone https://github.com/mike10004/httprequestecho
    $ cd httprequestecho
    $ mvn install -Dhttprequestecho.project.id=YOUR_PROJECT_ID
    $ mvn gcloud:deploy

Add `-Dhttprequestecho.gcloud.gcloud_directory` to your Google Cloud SDK
directory if necessary. You may encounter some resistance from the Google Cloud
Maven plugin, which will need you to have installed some dependencies and
have done some configuration first.

