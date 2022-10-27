package br.com.beilke.fileprocessing;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class GooglePhotosFileFixer {

    private static final Logger logger = Logger.getLogger(GooglePhotosFileFixer.class);

    private static String photosDir;
    private static final String[] photoFormats = {"jpg", "jpeg", "png", "webp", "bmp", "tif", "tiff", "svg", "heic"};
    private static final String[] videoFormats = {"mp4", "gif", "mov", "webm", "avi", "wmv", "rm", "mpg", "mpe", "mpeg", "mkv", "m4v",
            "mts", "m2ts"};

    private static final String[] extraFormats = {
            "-edited", "-effects", "-smile", "-mix",
            "-edytowane",
            "-bearbeitet",
            "-bewerkt"};

    private static final String[] altSnippetFormats = {
            "-SNIPPE", "-SNIPP"};

    private static final String jsonExtension = "json";
    private static int noJsonFound = 0;
    private static int renamedFiles = 0;
    private static int jsonFileCreated = 0;

    public static void main(String[] args) throws IOException {
        if (args[0] == null || args[0].trim().isEmpty()) {
            System.err.println("You need to specify a path!");
        } else {
            photosDir = args[0];
            logger.info("Processing started!");
            logger.info("Started to process the path: " + photosDir);

            GooglePhotosFileFixer googlePhotosFileFixer = new GooglePhotosFileFixer();
            googlePhotosFileFixer.doJsonNonStandardFilenameProcess();
            googlePhotosFileFixer.doJsonMissingProcess();

            logger.info("Processing finished!");
            logger.info("Processing statistics");
            logger.info("JSON Files created: " + jsonFileCreated);
            logger.info("Renamed JSON files: " + renamedFiles);
            logger.info("JSON Files not found: " + noJsonFound);
        }
    }

    public static Optional<String> getExtensionByStringHandling(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }

    private static void fixMissingJson(Path f, String fileExtension) {
        String fileName = f.getFileName().toString();

        String filenameSubstring = fileName.substring(0, fileName.indexOf(fileExtension) - 1);

        String jsonFileName = filenameSubstring + "." + fileExtension + "." + jsonExtension;
        String incorrectJsonFileName = filenameSubstring + "." + jsonExtension;

        Path pathFromFile = f.getParent();

        Path newPath = pathFromFile.resolve(jsonFileName);
        Path newPathFromFile = pathFromFile.resolve(incorrectJsonFileName);

        boolean hasJson = Files.exists(newPath);
        boolean hasIncorrectJson = Files.exists(newPathFromFile);

        if (!hasJson) // JSON not found
        {
            if (hasIncorrectJson) {
                renameFile(newPathFromFile, newPath);
            } else {
                String extraFormat = getExtraFormat(fileName);

                if (!"".equalsIgnoreCase(extraFormat)) // Found an extra file
                {
                    String editedJsonFile = fileName.substring(0, fileName.indexOf(extraFormat)) + "." + fileExtension + "." + jsonExtension;

                    Path originalJson = pathFromFile.resolve(editedJsonFile);
                    boolean isEdited = Files.exists(originalJson);

                    if (isEdited) {
                        // copy the JSON from the original photo to the edited one
                        Path pathDestiny = Paths.get(f + "." + jsonExtension);
                        createNewFile(pathDestiny, originalJson);
                    }
                } else {
                    logger.info("No json file found for the file: "+fileName);
                    noJsonFound++;
                }
            }
        }
    }

    private static String getExtraFormat(String fileName) {
        for (String extraFormat : extraFormats)
            if (fileName.contains(extraFormat))
                return extraFormat;
        return "";
    }

    private static String[] getWrongExtraFormat(String fileName) {
        String[] list = new String[2];
        String effectFilename = "-EFFECTS";

        if (fileName.contains(effectFilename)) {
            return list;
        } else if (fileName.contains("-EFFEC")) {
            list[0] = effectFilename;
            list[1] = "-EFFEC";
            return list;
        } else if (fileName.contains("-EFFE")) {
            list[0] = effectFilename;
            list[1] = "-EFFE";
            return list;
        }

        for (String extraFormat : altSnippetFormats)
            if (fileName.contains(extraFormat) && !fileName.contains("-SNIPPET")) {
                list[0] = "-SNIPPET";
                list[1] = extraFormat;
                return list;
            }
        return list;
    }

    private static void renameFile(Path oldFile, Path newFile) {
        try {
            Files.move(oldFile, newFile);
            logger.info(oldFile.getFileName() + " renamed to: " + newFile.getFileName());
        } catch (IOException ex) {
            logger.error("Rename failed" + oldFile.getFileName() + " renamed to: " + newFile.getFileName());
        }
        renamedFiles++;
    }

    private static void createNewFile(Path destiny, Path source) {
        try {
            Files.copy(source, destiny, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Json file created: " + destiny.getFileName());
        } catch (IOException ex) {
            logger.error("Json file creation failed" + destiny.getFileName());
        }
        jsonFileCreated++;
    }

    private void doJsonNonStandardFilenameProcess() throws IOException {

        logger.info("Started to fix non Standard file names!");

        long count;
        Path pathPhotosDir = Paths.get(photosDir);

        try (Stream<Path> walkStream2 = Files.walk(pathPhotosDir)) {
            count = walkStream2.count();
        }

        logger.info("Number of files found:" + count);

        ProgressBarBuilder pbb = new ProgressBarBuilder()
                .setTaskName("Processing")
                .setUnit(" files", 1).setInitialMax(count);
        try (Stream<Path> walkStream = Files.walk(pathPhotosDir)) {
            ProgressBar.wrap(walkStream.filter(p -> p.toFile().isFile()).parallel(), pbb).forEach(f -> {
                fixInvalidExtraFormatFileName(f);
                fixParenthesisPositionFileName(f);
            });
        }

        logger.info("Finished to fix non Standard File name!");
    }

    private void doJsonMissingProcess() throws IOException {
        Path pathPhotosDir = Paths.get(photosDir);

        logger.info("Started to fix missing JSON files! ");

        long count;
        try (Stream<Path> walkStream2 = Files.walk(pathPhotosDir)) {
            count = walkStream2.count();
        }

        logger.info("Number of files found:" + count);

        ProgressBarBuilder pbb = new ProgressBarBuilder()
                .setTaskName("Processing")
                .setUnit(" files", 1).setInitialMax(count);
        try (Stream<Path> walkStream = Files.walk(pathPhotosDir)) {
            ProgressBar.wrap(walkStream.filter(p -> p.toFile().isFile()).parallel(), pbb).forEach(f -> {

                String fileExtension = getFileExtension(f);

                boolean isValidFormat = Arrays.stream(photoFormats).anyMatch(fileExtension::equalsIgnoreCase) || Arrays.stream(videoFormats).anyMatch(fileExtension::equalsIgnoreCase);

                if (!"".equalsIgnoreCase(fileExtension) && isValidFormat)
                    fixMissingJson(f, fileExtension);
            });
        }

        logger.info("Finished to fix missing JSON files!");
    }

    private String getFileExtension(Path file) {
        Optional<String> extensionList = getExtensionByStringHandling(file.toString());

        if (extensionList.isPresent()) {
            return extensionList.get();
        }

        return "";
    }

    private static String getFileExtension(String file) {
        Optional<String> extensionList = getExtensionByStringHandling(file);

        if (extensionList.isPresent()) {
            return extensionList.get();
        }
        return "";
    }

    private void fixInvalidExtraFormatFileName(Path file) {
        String fileName = file.getFileName().toString();

        String[] formatList = getWrongExtraFormat(fileName);

        if (formatList[0] != null) // Found an extra file
        {
            String newFilename = fileName.replace(formatList[1], formatList[0]);

            Path directory = file.getParent();
            Path newPath = Paths.get(directory.toString(), newFilename);

            renameFile(file, newPath);
        }
    }

    private void fixParenthesisPositionFileName(Path file) {
        String mainFileExtension = getFileExtension(file);

        String oldFileName = file.getFileName().toString();

        final String regex2 = "\\([^\\d]*(\\d+)[^\\d]*\\)";

        List<String> regexAuxFileExtension = Arrays.asList(Pattern.compile(regex2, Pattern.CASE_INSENSITIVE).split(oldFileName));
        String auxFileExtension = getFileExtension(regexAuxFileExtension.get(0));

        String regex = "^[^\\(]*." + auxFileExtension + "\\(\\d*\\)\\." + mainFileExtension + "$";

        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(oldFileName);

        if (!"".equalsIgnoreCase(auxFileExtension) && m.find()) {
            regex = "^[^\\.]+";

            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(oldFileName);
            matcher.find();
            String isolatedFileName = matcher.group(0);

            int bIndex = oldFileName.indexOf("(");
            int eIndex = oldFileName.indexOf(").");

            String parenthesisWithNumber = oldFileName.substring(bIndex, eIndex + 1);

            String newFilename = isolatedFileName + parenthesisWithNumber + "." + auxFileExtension + "." + mainFileExtension;

            Path directory = file.getParent();
            Path newPath = Paths.get(directory.toString(), newFilename);

            renameFile(file, newPath);
        }
    }
}