package net.highwayfrogs.editor.games.sony.shared.map.packet;

import lombok.Getter;
import net.highwayfrogs.editor.Constants;
import net.highwayfrogs.editor.file.reader.DataReader;
import net.highwayfrogs.editor.file.standard.SVector;
import net.highwayfrogs.editor.file.writer.DataWriter;
import net.highwayfrogs.editor.games.sony.SCGameData;
import net.highwayfrogs.editor.games.sony.SCGameInstance;
import net.highwayfrogs.editor.games.sony.shared.SCByteTextureUV;
import net.highwayfrogs.editor.games.sony.shared.map.SCMapFile;
import net.highwayfrogs.editor.games.sony.shared.map.SCMapFilePacket;
import net.highwayfrogs.editor.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents polygon data for an SC game.
 * Created by Kneesnap on 5/7/2024.
 */
@Getter
public abstract class SCMapPolygonPacket<TGameInstance extends SCGameInstance> extends SCMapFilePacket<SCMapFile<TGameInstance>, TGameInstance> {
    public static final String IDENTIFIER = "POLY";
    private static final int VERTEX_GRID_SHIFT = 12;
    private static final int VERTEX_GRID_DIMENSIONS_FROM_CENTER = 16;
    private static final int VERTEX_GRID_DIMENSIONS_FULL = VERTEX_GRID_DIMENSIONS_FROM_CENTER * 2;
    private static final int VERTEX_GRID_ORIGIN = 32768;

    private final List<SCMapPolygon> polygons = new ArrayList<>();
    private final List<SVector> vertices = new ArrayList<>();
    private final List<SCMapPolygonUV> uvs = new ArrayList<>();
    private final short[][] vertexGridOffsetTable = new short[VERTEX_GRID_DIMENSIONS_FULL][VERTEX_GRID_DIMENSIONS_FULL]; // Contains vertex ids.
    private final short[][] vertexGridLengthTable = new short[VERTEX_GRID_DIMENSIONS_FULL][VERTEX_GRID_DIMENSIONS_FULL]; // Indices are shared between the offset table and the length table.

    public SCMapPolygonPacket(SCMapFile<TGameInstance> parentFile) {
        super(parentFile, IDENTIFIER);
    }

    @Override
    protected void loadBody(DataReader reader, int endIndex) {
        int polygonCount = reader.readUnsignedShortAsInt();
        int vertexCount = reader.readUnsignedShortAsInt();
        int uvCount = reader.readUnsignedShortAsInt();
        reader.skipBytesRequireEmpty(Constants.SHORT_SIZE);

        int polygonDataStartIndex = reader.readInt();
        int vertexDataStartIndex = reader.readInt();
        int uvDataStartIndex = reader.readInt();
        int gridOffsetTableStartIndex = reader.readInt();
        int gridLengthTableStartIndex = reader.readInt();

        // Read polygons.
        if (reader.getIndex() != polygonDataStartIndex)
            throw new RuntimeException("We expected to read polygon data, but it starts at " + Utils.toHexString(polygonDataStartIndex) + ", and the reader is at " + Utils.toHexString(reader.getIndex()) + ".");

        this.polygons.clear();
        for (int i = 0; i < polygonCount; i++) {
            SCMapPolygon newPolygon = createPolygon();
            newPolygon.load(reader);
            this.polygons.add(newPolygon);
        }

        // Read vertices.
        if (reader.getIndex() != vertexDataStartIndex)
            throw new RuntimeException("We expected to read vertex data, but it starts at " + Utils.toHexString(vertexDataStartIndex) + ", and the reader is at " + Utils.toHexString(reader.getIndex()) + ".");

        this.vertices.clear();
        for (int i = 0; i < vertexCount; i++)
            this.vertices.add(SVector.readWithPadding(reader));

        // Read grid offset table.
        if (reader.getIndex() != gridOffsetTableStartIndex)
            throw new RuntimeException("We expected to read the grid offset table, but it starts at " + Utils.toHexString(gridOffsetTableStartIndex) + ", and the reader is at " + Utils.toHexString(reader.getIndex()) + ".");

        for (int y = 0; y < this.vertexGridOffsetTable.length; y++)
            for (int x = 0; x < this.vertexGridOffsetTable[y].length; x++)
                this.vertexGridOffsetTable[y][x] = reader.readShort();

        // Read grid length table.
        if (reader.getIndex() != gridLengthTableStartIndex)
            throw new RuntimeException("We expected to read the grid length table, but it starts at " + Utils.toHexString(gridLengthTableStartIndex) + ", and the reader is at " + Utils.toHexString(reader.getIndex()) + ".");

        for (int y = 0; y < this.vertexGridLengthTable.length; y++)
            for (int x = 0; x < this.vertexGridLengthTable[y].length; x++)
                this.vertexGridLengthTable[y][x] = reader.readShort();

        // Read UVs.
        if (reader.getIndex() != uvDataStartIndex)
            throw new RuntimeException("We expected to read UV data, but it starts at " + Utils.toHexString(uvDataStartIndex) + ", and the reader is at " + Utils.toHexString(reader.getIndex()) + ".");

        this.uvs.clear();
        for (int i = 0; i < uvCount; i++) {
            SCMapPolygonUV newUv = new SCMapPolygonUV(getParentFile().getGameInstance());
            newUv.load(reader);
            this.uvs.add(newUv);
        }
    }

    @Override
    protected void saveBodyFirstPass(DataWriter writer) {
        writer.writeUnsignedShort(this.polygons.size());
        writer.writeUnsignedShort(this.vertices.size());
        writer.writeUnsignedShort(this.uvs.size());
        writer.writeUnsignedShort(0); // Padding

        // Write pointers.
        int polygonDataStartIndex = writer.writeNullPointer();
        int vertexDataStartIndex = writer.writeNullPointer();
        int uvDataStartIndex = writer.writeNullPointer();
        int gridOffsetTableStartIndex = writer.writeNullPointer();
        int gridLengthTableStartIndex = writer.writeNullPointer();

        // Read polygons.
        writer.writeAddressTo(polygonDataStartIndex);
        for (int i = 0; i < this.polygons.size(); i++)
            this.polygons.get(i).save(writer);

        // Write vertices.
        writer.writeAddressTo(vertexDataStartIndex);
        for (int i = 0; i < this.vertices.size(); i++)
            this.vertices.get(i).save(writer);

        // Write grid offset table.
        writer.writeAddressTo(gridOffsetTableStartIndex);
        for (int y = 0; y < this.vertexGridOffsetTable.length; y++)
            for (int x = 0; x < this.vertexGridOffsetTable[y].length; x++)
                writer.writeShort(this.vertexGridOffsetTable[y][x]);

        // Write grid length table.
        writer.writeAddressTo(gridLengthTableStartIndex);
        for (int y = 0; y < this.vertexGridLengthTable.length; y++)
            for (int x = 0; x < this.vertexGridLengthTable[y].length; x++)
                writer.writeShort(this.vertexGridLengthTable[y][x]);

        // Write UVs.
        writer.writeAddressTo(uvDataStartIndex);
        for (int i = 0; i < this.uvs.size(); i++)
            this.uvs.get(i).save(writer);
    }

    /**
     * Creates a new polygon instance.
     */
    public abstract SCMapPolygon createPolygon();

    @Getter
    public static class SCMapPolygonUV extends SCGameData<SCGameInstance> {
        private final SCByteTextureUV[] textureUvs = new SCByteTextureUV[4];

        public SCMapPolygonUV(SCGameInstance instance) {
            super(instance);
            for (int i = 0; i < this.textureUvs.length; i++)
                this.textureUvs[i] = new SCByteTextureUV();
        }

        @Override
        public void load(DataReader reader) {
            for (int i = 0; i < this.textureUvs.length; i++)
                this.textureUvs[i].load(reader);
        }

        @Override
        public void save(DataWriter writer) {
            for (int i = 0; i < this.textureUvs.length; i++)
                this.textureUvs[i].save(writer);
        }
    }
}