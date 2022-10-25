package br.com.beilke.fileprocessing;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class GooglePhotosFileFixer {

    static final Logger logger = Logger.getLogger(GooglePhotosFileFixer.class);
    //private static final String PHOTOS_DIR = "E:\\Takeout\\3";
    private static final String PHOTOS_DIR = "C:\\Users\\febeil\\Downloads\\collages\\Takeout\\Google Photos\\Collage";
    private static final String[] photo_formats = {"jpg", "jpeg", "png", "webp", "bmp", "tif", "tiff", "svg", "heic"};
    private static final String[] video_formats = {"mp4", "gif", "mov", "webm", "avi", "wmv", "rm", "mpg", "mpe", "mpeg", "mkv", "m4v",
            "mts", "m2ts"};

    private static final String[] extra_formats = {
            "-edited", "-effects", "-smile", "-mix",
            "-edytowane",
            "-bearbeitet",
            "-bewerkt"};

    private static final String[] altSnippet_formats = {
            "-SNIPPE", "-SNIPP"};

    private static final String JSON = "json";
    private static int no_json_found = 0;
    private static int renamed_files = 0;
    private static int json_file_created = 0;

    private static int filesCount = 0;

    private static final Path pathPhotosDir = Paths.get(PHOTOS_DIR);

    public static void main(String[] args) throws IOException {
        logger.info("Processing started!");
        logger.info("Started to process the path: " + PHOTOS_DIR);

        GooglePhotosFileFixer googlePhotosFileFixer = new GooglePhotosFileFixer();
        googlePhotosFileFixer.doJsonNonStandardFilenameProcess();
        googlePhotosFileFixer.doJsonMissingProcessWithProgressbar();

        logger.info("Processing finished!");
        logger.info("Processing statistics");
        logger.info("JSON Files created: " + json_file_created);
        logger.info("Renamed JSON files: " + renamed_files);
        logger.info("JSON Files not found: " + no_json_found);
    }

    public static Optional<String> getExtensionByStringHandling(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }

    private static void fixMissingJson(Path f, String fileExtension) {
        String fileName = f.getFileName().toString();

        String filenameSubstring = fileName.substring(0, fileName.indexOf(fileExtension) - 1);

        String jsonFileName = filenameSubstring + "." + fileExtension + "." + JSON;
        String incorrectJsonFileName = filenameSubstring + "." + JSON;

        Path pathFromFile =  Paths.get(PHOTOS_DIR);

        boolean hasJson = Files.exists(Paths.get(PHOTOS_DIR+ "\\" + jsonFileName));
        boolean hasIncorrectJson = Files.exists(Paths.get(PHOTOS_DIR+ "\\" + incorrectJsonFileName));

        if (!hasJson) // JSON not found
        {
            if (hasIncorrectJson) {
                Path newPath = Paths.get(PHOTOS_DIR+ "\\" + jsonFileName);
                Path newPathFromFile = pathFromFile.resolve(incorrectJsonFileName);
                renameFile(newPathFromFile, newPath);
            } else {
                String extraFormat = getExtraFormat(fileName);

                if (!"".equalsIgnoreCase(extraFormat)) // Found an extra file
                {
                    String editedJsonFile = fileName.substring(0, fileName.indexOf(extraFormat)) + "." + fileExtension + "." + JSON;

                    boolean isEdited = Files.exists(Paths.get(PHOTOS_DIR+ "\\" + editedJsonFile));

                    if (isEdited) {
                        // copy the JSON from the original photo to the edited one
                        Path originalJson = Paths.get(PHOTOS_DIR+ "\\" + editedJsonFile);

                        Path pathDestiny = Paths.get(f + "." + JSON);

                        createNewFile(pathDestiny, originalJson);
                    }
                } else {
                    no_json_found++;
                }
            }
        }
    }

    private static String getExtraFormat(String fileName) {
        for (String extraFormat : extra_formats)
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

        for (String extraFormat : altSnippet_formats)
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
        renamed_files++;
    }

    private static void createNewFile(Path destiny, Path source) {
        try {
            Files.copy(source, destiny, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Json file created: " + destiny.getFileName());
        } catch (IOException ex) {
            logger.error("Json file creation failed" + destiny.getFileName());
        }
        json_file_created++;
    }

    private void doJsonNonStandardFilenameProcess() throws IOException {

        logger.info("Started to fix non Standard file names!");

        long count = 0;

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

    private void doJsonMissingProcessWithProgressbar() throws IOException {

        logger.info("Started to fix missing JSON files! ");

        long count = 0;
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

                boolean isValidFormat = Arrays.stream(photo_formats).anyMatch(fileExtension::equalsIgnoreCase) || Arrays.stream(video_formats).anyMatch(fileExtension::equalsIgnoreCase);

                if (!"".equalsIgnoreCase(fileExtension) && isValidFormat)
                    fixMissingJson(f, fileExtension);
            });

        }

        logger.info("Finished to fix missing JSON files!");
    }

    private String getFileExtension(Path f) {
        Optional<String> extensionList = getExtensionByStringHandling(f.toString());

        if (extensionList.isPresent()) {
            return extensionList.get();
        }

        return "";
    }

    private static String getFileExtension(String f) {
        Optional<String> extensionList = getExtensionByStringHandling(f);

        if (extensionList.isPresent()) {
            return extensionList.get();
        }
        return "";
    }

    private void fixInvalidExtraFormatFileName(Path f) {
        String fileName = f.getFileName().toString();

        String[] formatList = getWrongExtraFormat(fileName);

        if (formatList[0] != null) // Found an extra file
        {
            String newFilename = fileName.replace(formatList[1], formatList[0]);

            Path directory = f.getParent();
            Path newPath = Paths.get(directory + "\\" + newFilename);

            renameFile(f, newPath);
        }
    }

    private void fixParenthesisPositionFileName(Path f) {
        String mainFileExtension = getFileExtension(f);

        String oldFileName = f.getFileName().toString();

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

            Path directory = f.getParent();
            Path newPath = Paths.get(directory + "\\" + newFilename);

            renameFile(f, newPath);
        }
    }
}