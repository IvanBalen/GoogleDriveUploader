package gdrive.upload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.common.io.Files;

/**
 * this class contains the main logic for transferring files from a local folder to Google drive
 * note that in order to use this application, you must first acquire the credentials file
 * from Google Cloud Platform (any user with a valid Gmail account can do this )
 *
 */
@Service
public class DriveUploader {
	
	private static final String APPLICATION_NAME = "DriveUploader";

	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	
    private static final java.io.File CREDENTIALS_FOLDER = new java.io.File("credentials");
    
    private static Optional<Drive> driveServiceOpt;
    
    @Value("${gdrive.root}")
    private String rootFolder;
    
    @Value("${gdrive.done}")
    private String doneFolder;

	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
    	
		DriveUploader du = new DriveUploader();
		du.rootFolder = "C:\\gdrive\\upload";
		du.doneFolder = "C:\\gdrive\\done";
		du.syncGDrive();
				
	}
	/**
	 * this method syncs the local folder files (contained inside rootFolder) with Google drive
	 * the actual syncing time is specified in application.properties
	 * or we can call this method directly from main()
	 * to make advantage of @Scheduled, you have to run the app as a Spring Boot application
	 * @throws Exception
	 */
	@Scheduled(cron = "${gdrive.scheduler.time}")
    public void syncGDrive() throws Exception {
    	
    	driveServiceOpt = Optional.ofNullable( getDriveService() );
		
		driveServiceOpt.ifPresentOrElse( drive -> 
		{
		Map<String, List<String>> filesMap = null;
			try {
				filesMap = FilesCollector.getAllFilesByFolder(Paths.get(rootFolder));
			} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			}
			try {
				uploadFiles(filesMap, drive);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		,() -> System.out.println("Google drive is currently unavailable!"));
    }
	/**
	 * this method copies the uploaded files to doneFolder and then deletes the original local file
	 * this is done to prevent uploading the same files again
	 * @param file
	 * @throws IOException
	 */
	private void moveFile(File file) throws IOException {
		
		Files.copy(file, Path.of(doneFolder, file.getName()).toFile());
		file.delete();
		
	}
	/**
	 * this method uploads files from a local folder to google drive, mirroring the local folder structure
	 * @param filesMap -  map that holds the folder paths as keys and file names as values
	 * @param driveService
	 * @throws IOException
	 */
	public  void uploadFiles(Map<String, List<String>> filesMap, Drive driveService) throws IOException  {
		
		//this map holds the folder names as keys and folder ids as values -- 
		//it helps us avoid creating duplicate folders on Google drive
		HashMap<String, String> createdFolders = new HashMap<>();
		if(!filesMap.isEmpty()) {
			
			Iterator<Entry<String,List<String>>> fileIter = filesMap.entrySet().iterator();
			//the main loop iterating through all the files
			while(fileIter.hasNext()) {
				
				Entry<String,List<String>> entry = fileIter.next();
				
				String folderPath 		= entry.getKey();
				List<String> fileList 	= entry.getValue();
				
				String[] folders = folderPath.split("\\\\");
				
				createFolderStructure(folders, createdFolders, driveService);
				
				String currFolder = folders[folders.length-1];
				
				for(String file : fileList) {
					
					String folderId = createdFolders.get(currFolder);
					
					Path path = Path.of(folderPath , file);
					File localFile = path.toFile();
					FileContent mediaContent = new FileContent(null, localFile);
						
					com.google.api.services.drive.model.File fileMetadata = 
							new com.google.api.services.drive.model.File();
					fileMetadata.setName(file);
					fileMetadata.setParents(Collections.singletonList(folderId));
					
					System.out.println("uploading file: " + path.toString());

					driveService.files()
							.create(fileMetadata, mediaContent)
						    .setFields("id")
						    .execute();
					System.out.println("file uploaded: " + path.toString());
					moveFile(localFile);
					
				}
			}
		}
	}
	/**
	 * this method creates folders on Google drive and populates createdFolders map
	 * @param folders - array of folders that corresponds to the path of the file being 
	 * currently processed (e.g C:\dir2\dir2\file.txt will result in [ C:, dir1, dir2 ] 
	 * @param createdFolders - map holding the created folder names and ids 
	 * @param driveService
	 * @throws IOException
	 */
	private  void createFolderStructure(final String[] folders, final Map<String, String> createdFolders, 
			Drive driveService) throws IOException {
		
		for(int i=1; i<folders.length;++i) {
			String folderName = folders[i];
			if (!createdFolders.containsKey(folderName)) {
				
				com.google.api.services.drive.model.File folderMetadata = new com.google.api.services.drive.model.File();
				folderMetadata.setName(folderName);
				folderMetadata.setMimeType("application/vnd.google-apps.folder");
				folderMetadata.setParents(Collections.singletonList( i>1 ? createdFolders.get(folders[i-1]) : null ));				
				
				com.google.api.services.drive.model.File folder = driveService.files().create(folderMetadata)
					    .setFields("id")
					    .execute();
				
				createdFolders.put(folderName, folder.getId());
				
			}
		}
	}
	/**
	 * this method retrieves the Google drive service instance
	 * the prerequisite is that we need to have a credentials file based on OAuth2
	 * (this file can be acquired via Google Cloud Platform )
	 * @return - Google drive service instance
	 * @throws Exception
	 */
    private  Drive getDriveService() {
        InputStream in = DriveUploader.class.getResourceAsStream("/credentials/cred.json");
        
        try {
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, clientSecrets,
                Collections.singletonList(DriveScopes.DRIVE_FILE))
                .setDataStoreFactory(new FileDataStoreFactory(CREDENTIALS_FOLDER))
                .setAccessType("offline")
                .build();
    

        return new Drive.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY,
                new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("me"))
                .setApplicationName(APPLICATION_NAME)
                .build();
        }catch(Exception e) {
        	return null;
        }
    }

}
