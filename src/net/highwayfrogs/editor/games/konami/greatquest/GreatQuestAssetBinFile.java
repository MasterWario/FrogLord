package net.highwayfrogs.editor.games.konami.greatquest;

import lombok.Getter;
import net.highwayfrogs.editor.file.config.Config;
import net.highwayfrogs.editor.file.reader.ArraySource;
import net.highwayfrogs.editor.file.reader.DataReader;
import net.highwayfrogs.editor.file.reader.FileSource;
import net.highwayfrogs.editor.file.writer.ArrayReceiver;
import net.highwayfrogs.editor.file.writer.DataWriter;
import net.highwayfrogs.editor.games.generic.GameData;
import net.highwayfrogs.editor.games.konami.greatquest.loading.kcLoadContext;
import net.highwayfrogs.editor.games.konami.greatquest.model.kcModelWrapper;
import net.highwayfrogs.editor.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

/**
 * Parses FTGQ's main game data file. It's called "data.bin" in all of the builds we've seen.
 * .SBR files contain sound effects. the SCK file contains only PCM data, with headers in the .IDX file.
 * .PSS (PS2) are video files. Can be opened with VLC.
 * BUFFER.DAT files (PS2) are video files, and can be opened with VLC.
 * TODO: kcOpen() accesses a global flag that tells the game if it should read files from the filesystem or from a .bin file. We could consider a mod to the game to have it load from the file system.
 * Created by Kneesnap on 8/17/2019.
 */
@Getter
public class GreatQuestAssetBinFile extends GameData<GreatQuestInstance> {
    private List<String> globalPaths;
    private final List<GreatQuestArchiveFile> files = new ArrayList<>();
    private final Map<Integer, GreatQuestArchiveFile> nameMap = new HashMap<>();
    private final Map<Integer, List<GreatQuestArchiveFile>> fileCollisions = new HashMap<>();

    private static final int NAME_SIZE = 0x108;

    public GreatQuestAssetBinFile(GreatQuestInstance gameInstance) {
        super(gameInstance);
    }

    @Override
    public void load(DataReader reader) {
        int unnamedFiles = reader.readInt();
        int namedFiles = reader.readInt();

        // Read Names. (Located after all the files)
        int nameAddress = reader.readInt();
        reader.jumpTemp(nameAddress);
        int nameCount = reader.readInt();
        this.globalPaths = new ArrayList<>(nameCount);
        for (int i = 0; i < nameCount; i++)
            this.globalPaths.add(reader.readTerminatedStringOfLength(NAME_SIZE));
        reader.jumpReturn();

        Map<Integer, String> nameMap = new HashMap<>();
        GreatQuestUtils.addHardcodedFileNameHashesToMap(nameMap);

        // Read unnamed files.
        for (int i = 0; i < unnamedFiles; i++) {
            int hash = reader.readInt();
            readFile(reader, nameMap.get(hash), hash, false);
        }

        // Read named files. Files are named if they have a collision with other files.
        for (int i = 0; i < namedFiles; i++) {
            String fullFilePath = reader.readTerminatedStringOfLength(NAME_SIZE);
            readFile(reader, fullFilePath, GreatQuestUtils.hashFilePath(fullFilePath), true);
        }

        // Handle post-load setup.
        kcLoadContext context = new kcLoadContext(this);
        for (int i = 0; i < this.files.size(); i++)
            this.files.get(i).afterLoad1(context);
        for (int i = 0; i < this.files.size(); i++)
            this.files.get(i).afterLoad2(context);

        context.onComplete();
    }

    private GreatQuestArchiveFile readFile(DataReader reader, String name, int crc, boolean hasCollision) {
        int size = reader.readInt();
        int zSize = reader.readInt();
        int offset = reader.readInt();
        int zero = reader.readInt();
        if (zero != 0)
            throw new RuntimeException("File field was supposed to be zero! Was: " + zero);

        boolean isCompressed = (zSize != 0); // ZLib compression.

        reader.jumpTemp(offset);
        byte[] fileBytes = isCompressed ? GreatQuestUtils.zlibDecompress(reader.readBytes(zSize), size) : reader.readBytes(size);
        reader.jumpReturn();

        GreatQuestArchiveFile readFile;
        if (Utils.testSignature(fileBytes, GreatQuestImageFile.SIGNATURE_STR)) {
            readFile = new GreatQuestImageFile(getGameInstance());
        } else if (Utils.testSignature(fileBytes, kcModelWrapper.SIGNATURE_STR)) {
            readFile = new kcModelWrapper(getGameInstance());
        } else if (Utils.testSignature(fileBytes, "TOC\0")) {
            readFile = new GreatQuestChunkedFile(getGameInstance());
        } else if (this.files.size() > 100 && fileBytes.length > 30) {
            readFile = new GreatQuestImageFile(getGameInstance());
        } else {
            readFile = new GreatQuestDummyArchiveFile(getGameInstance(), fileBytes.length);
        }

        // Setup file.
        readFile.init(name, isCompressed, crc, fileBytes, hasCollision);
        this.files.add(readFile); // Add before loading, so it can find its ID.
        if (hasCollision) {
            this.fileCollisions.computeIfAbsent(readFile.getNameHash(), key -> new ArrayList<>()).add(readFile);
        } else {
            this.nameMap.put(readFile.getNameHash(), readFile);
        }

        // Read file.
        try {
            DataReader fileReader = new DataReader(new ArraySource(fileBytes));
            readFile.load(fileReader);
        } catch (Exception ex) {
            throw new RuntimeException("There was a problem reading " + readFile.getClass().getSimpleName() + " [File " + (this.files.size() - 1) + "]", ex);
        }

        return readFile;
    }

    @Override
    public void save(DataWriter writer) {
        List<GreatQuestArchiveFile> unnamedFiles = new ArrayList<>();
        List<GreatQuestArchiveFile> namedFiles = new ArrayList<>();
        for (GreatQuestArchiveFile file : getFiles())
            (file.hasFilePath() && file.isCollision() ? namedFiles : unnamedFiles).add(file);

        // Start writing file.
        writer.writeInt(unnamedFiles.size());
        writer.writeInt(namedFiles.size());
        int nameAddress = writer.writeNullPointer();

        Map<GreatQuestArchiveFile, Integer> fileSizeTable = new HashMap<>();
        Map<GreatQuestArchiveFile, Integer> fileZSizeTable = new HashMap<>();
        Map<GreatQuestArchiveFile, Integer> fileOffsetTable = new HashMap<>();

        // Write file headers:
        for (GreatQuestArchiveFile file : getFiles()) {
            if (file.hasFilePath() && file.isCollision()) {
                int endIndex = (writer.getIndex() + NAME_SIZE);
                writer.writeTerminatorString(file.getFilePath());
                writer.writeTo(endIndex, (byte) 0xCD);
            } else {
                writer.writeInt(file.getNameHash());
            }

            fileSizeTable.put(file, writer.getIndex());
            writer.writeInt(0); // Size.
            fileZSizeTable.put(file, writer.getIndex());
            writer.writeInt(0); // ZSize.
            fileOffsetTable.put(file, writer.getIndex());
            writer.writeInt(0); // Offset.
            writer.writeInt(0); // Zero.
        }

        // Write files:
        for (GreatQuestArchiveFile file : getFiles()) {
            writer.writeAddressTo(fileOffsetTable.get(file));
            getLogger().info("Saving " + file.getFilePath());

            byte[] rawBytes; // TODO :TOSS
            if (!(file instanceof GreatQuestChunkedFile) && file.getRawData() != null) {
                // TODO: Seems both models and images are busted.
                // TODO: We're missing nearly 100MB of texture data when we let textures save themselves.
                rawBytes = file.getRawData();
                getLogger().info("Wrote " + rawBytes.length + " raw bytes.");
            } else {
                ArrayReceiver receiver = new ArrayReceiver();
                file.save(new DataWriter(receiver));
                rawBytes = receiver.toArray();
                getLogger().info("Wrote " + rawBytes.length + " bytes.");
            }

            // Write size.
            writer.jumpTemp(fileSizeTable.get(file));
            writer.writeInt(rawBytes.length);
            writer.jumpReturn();

            // File is compressed.
            if (file.isCompressed()) {
                rawBytes = GreatQuestUtils.zlibCompress(rawBytes); // Compress data.

                // Write z size.
                writer.jumpTemp(fileZSizeTable.get(file));
                writer.writeInt(rawBytes.length);
                writer.jumpReturn();
            }

            writer.writeBytes(rawBytes);
        }

        // Lastly, write global strings.
        writer.writeAddressTo(nameAddress);
        writer.writeInt(this.globalPaths.size());
        for (String name : getGlobalPaths()) {
            int endIndex = (writer.getIndex() + NAME_SIZE);
            writer.writeStringBytes(name);
            writer.writeTo(endIndex);
        }
    }

    /**
     * Print a list of all files to stdout.
     */
    @SuppressWarnings("unused")
    public void printFileList() {
        StringBuilder fileLine = new StringBuilder();
        for (int i = 0; i < this.files.size(); i++) {
            GreatQuestArchiveFile file = this.files.get(i);
            fileLine.append(file.hasFilePath() ? file.getFilePath() : "UNKNOWN");
            fileLine.append(" # File ");
            fileLine.append(Utils.padNumberString(i, 4));
            fileLine.append(", Hash: ");
            fileLine.append(Utils.to0PrefixedHexString(file.getNameHash()));
            if (file.isCollision())
                fileLine.append(" (Collision)");
            if (file.isCompressed())
                fileLine.append(", Compressed");
            getLogger().info(fileLine.toString());
            fileLine.setLength(0);
        }
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        String fileName;
        if (args.length > 0) {
            fileName = String.join(" ", args);
        } else {
            System.out.print("Please enter the file path to data.bin: ");
            fileName = scanner.nextLine();
        }

        if (Utils.stripAlphanumeric(fileName).equalsIgnoreCase("hash"))
            GreatQuestHashReverser.runHashPlayground();

        File binFile = new File(fileName);
        if (!binFile.exists() || !binFile.isFile()) {
            System.out.println("That is not a valid file!");
            return;
        }

        // Determine platform.
        Config config = getConfiguration(scanner);

        // Load main bin.
        System.out.println("Loading file...");
        DataReader reader = new DataReader(new FileSource(binFile));
        GreatQuestInstance instance = new GreatQuestInstance();
        instance.loadGame(config.getName(), config, binFile);
        instance.getMainArchive().load(reader);

        // Export.
        /*File exportDir = new File(binFile.getParentFile(), "Export");
        Utils.makeDirectory(exportDir);
        exportFileList(exportDir, mainFile);
        for (GreatQuestArchiveFile file : mainFile.getFiles())
            file.export(exportDir);*/

        instance.setupMainMenuWindow();
    }

    private static Config getConfiguration(Scanner scanner) {
        String configName;
        InputStream inputStream;
        do {
            System.out.print("Please enter the name of the configuration: ");
            configName = scanner.nextLine();

            String fullPath = "games/greatquest/versions/" + configName + ".cfg";
            inputStream = Utils.getResourceStream(fullPath);
            if (inputStream == null)
                System.out.println("Invalid configuration, please try again.");
        } while (inputStream == null);

        return new Config(inputStream, configName);
    }

    /**
     * Activates the filename
     * @param filePath              The path of a game file.
     * @param showMessageIfNotFound Specify if a warning should be displayed if the file is not found.
     */
    public GreatQuestArchiveFile applyFileName(String filePath, boolean showMessageIfNotFound) {
        GreatQuestArchiveFile file = getOptionalFileByName(filePath);
        if (file != null) {
            file.setFilePath(filePath);
            return file;
        }

        int hash = GreatQuestUtils.hashFilePath(filePath);
        if (showMessageIfNotFound)
            getLogger().warning("Attempted to apply the file path '" + filePath + "', but no file matched the hash " + Utils.to0PrefixedHexString(hash) + ".");
        return null;
    }

    /**
     * Searches for a file by its full file path, printing a message if the file is nto found.
     * @param searchFrom The file to search from, so that we can print which file wanted the file if it wasn't found.
     * @param filePath   Full file path.
     * @return the found file, if there was one.
     */
    public GreatQuestArchiveFile getFileByName(GreatQuestArchiveFile searchFrom, String filePath) {
        GreatQuestArchiveFile file = getOptionalFileByName(filePath);
        if (file == null)
            getLogger().warning("Failed to find file " + filePath + (searchFrom != null ? " referenced in " + searchFrom.getExportName() : "") + ". (" + GreatQuestUtils.getFileIdFromPath(filePath) + ")");
        return file;
    }

    /**
     * Searches for a file by its full file path.
     * @param filePath Full file path.
     * @return the found file, if there was one.
     */
    public GreatQuestArchiveFile getOptionalFileByName(String filePath) {
        // Create hash.
        String abbreviatedFilePath = GreatQuestUtils.getFileIdFromPath(filePath);
        int hash = GreatQuestUtils.hash(abbreviatedFilePath);

        // Search for unique file without collisions.
        GreatQuestArchiveFile file = this.nameMap.get(hash);
        if (file != null)
            return file;

        // Search colliding files.
        List<GreatQuestArchiveFile> collidingFiles = this.fileCollisions.get(hash);
        if (collidingFiles != null) {
            for (int i = 0; i < collidingFiles.size(); i++) {
                GreatQuestArchiveFile collidedFile = collidingFiles.get(i);
                if (GreatQuestUtils.getFileIdFromPath(collidedFile.getFilePath()).equalsIgnoreCase(abbreviatedFilePath))
                    return collidedFile;
            }
        }

        return null;
    }

    public static void exportFileList(File baseFolder, GreatQuestAssetBinFile mainArchive) throws IOException {
        List<String> lines = new ArrayList<>();
        long namedCount = mainArchive.getFiles().stream().filter(file -> file.getFilePath() != null).count();
        lines.add("File List [" + mainArchive.getFiles().size() + ", " + namedCount + " named]:");

        for (int i = 0; i < mainArchive.getFiles().size(); i++) {
            GreatQuestArchiveFile file = mainArchive.getFiles().get(i);

            lines.add(" - File #" + Utils.padNumberString(i, 4)
                    + ": " + Utils.to0PrefixedHexString(file.getNameHash())
                    + ", " + file.getClass().getSimpleName()
                    + (file.getFilePath() != null ? ", " + file.getFilePath() + ", " + GreatQuestUtils.getFileIdFromPath(file.getFilePath()) : ""));
        }

        lines.add("");
        lines.add("Global Paths:");
        for (String globalPath : mainArchive.getGlobalPaths())
            lines.add(" - " + globalPath);

        Files.write(new File(baseFolder, "file-list.txt").toPath(), lines);
    }
}