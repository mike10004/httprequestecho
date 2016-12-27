# HTTP Request Echo App Engine Web App

This is a web app that helps you debug web tools by echoing the content of your HTTP
requests right back to you. It is deployable on Google App Engine.

After you create a Google Cloud project through the developer console, take note of 
the project ID, clone this repository, and you can deploy the app with:

    $ mvn gcloud:deploy -Dhttprequestecho.project.id=YOUR_PROJECT_ID

Add `-Dhttprequestecho.gcloud.gcloud_directory` to point to your Google Cloud SDK 
directory if necessary. You may encounter some resistance from the SDK, which needs
some initial setup to be performed, such as installing the App Engine components and 
obtaining an access token.
