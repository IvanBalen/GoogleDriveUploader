Drive uploader is a simple Google drive uploader application

- you must be a gmail account user to use this application and you have to set up the DriveUploader project and acquire an Oauth2 authorization credentials file through **Google Cloud Platform** ( https://console.cloud.google.com/ )

- it can be used a Spring Boot app as well as a regular Java application ( more info can be found in the source code )

- if used as a Spring Boot app, it leverages a @Scheduled annotated method to automatically trigger Google drive file uploads (the upload times are parameterized in application.properties)

- if used as a regular Java app, the DriveUploader.main method is called, which, in turn, invokes the method for uploading files 

- the local folder structure is mirrored on Google drive (there is a constaint that your local folder structure should not contain duplicate folder names across different folder paths)