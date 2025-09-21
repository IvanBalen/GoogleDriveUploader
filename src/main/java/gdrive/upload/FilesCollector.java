package gdrive.upload;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * this is a helper class that is used to retrieve file names (paths) from a specified root folder
 
 *
 */
public class FilesCollector {

	/**
	 * 
	 * @param rootFolder - folder 
	 * @return - map that holds the paths and file names ( e.g { "C:\myfolder", "myFile.txt"}
	 * @throws IOException
	 */
    public static Map<String, List<String>> getAllFilesByFolder(Path rootFolder) throws IOException {
        Map<String, List<String>> folderToFilesMap = new HashMap<>();

        Files.walkFileTree(rootFolder, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path parent = file.getParent();
                String folderPath = parent.toString();
                folderToFilesMap.computeIfAbsent(folderPath, k -> new ArrayList<>())
                                .add(file.getFileName().toString());

                return FileVisitResult.CONTINUE;
            }
        });

        return folderToFilesMap;
    }

    public static void main(String[] args) throws IOException {
        Path root = Paths.get("C:/gdrive/"); // Change to your target folder
        Map<String, List<String>> result = getAllFilesByFolder(root);

        result.forEach((folder, files) -> {
            System.out.println("Folder: " + folder);
            files.forEach(file -> System.out.println("  - " + file));
        });
    }
}