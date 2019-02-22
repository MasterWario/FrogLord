package net.highwayfrogs.editor.file.standard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.highwayfrogs.editor.Constants;
import net.highwayfrogs.editor.Utils;
import net.highwayfrogs.editor.file.GameObject;
import net.highwayfrogs.editor.file.reader.DataReader;
import net.highwayfrogs.editor.file.writer.DataWriter;

/**
 * Vector comprised of integers.
 * Created by Kneesnap on 8/24/2018.
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class IVector extends GameObject implements Vector {
    private int x;
    private int y;
    private int z;

    public static final int UNPADDED_BYTE_SIZE = 3 * Constants.INTEGER_SIZE;
    public static final int PADDED_BYTE_SIZE = UNPADDED_BYTE_SIZE + Constants.INTEGER_SIZE;

    @Override
    public void load(DataReader reader) {
        this.x = reader.readInt();
        this.y = reader.readInt();
        this.z = reader.readInt();
    }

    /**
     * Load an IVector with an extra 4 bytes of padding.
     * @param reader The reader to read from.
     */
    public void loadWithPadding(DataReader reader) {
        this.load(reader);
        reader.skipInt();
    }

    @Override
    public void save(DataWriter writer) {
        writer.writeInt(getX());
        writer.writeInt(getY());
        writer.writeInt(getZ());
    }

    /**
     * Write an IVector with an extra 4 bytes of padding.
     * @param writer The writer to write data to.
     */
    public void saveWithPadding(DataWriter writer) {
        save(writer);
        writer.writeNull(Constants.INTEGER_SIZE);
    }

    /**
     * Equivalent to MR_ADD_VEC(b)
     * @param other The vector to add.
     */
    public void add(IVector other) {
        this.x += other.x;
        this.y += other.y;
        this.z += other.z;
    }

    /**
     * Gets the float X value.
     * @return floatX
     */
    public float getFloatX() {
        return Utils.fixedPointIntToFloatNBits(getX(), 4);
    }

    /**
     * Gets the float Y value.
     * @return floatY
     */
    public float getFloatY() {
        return Utils.fixedPointIntToFloatNBits(getY(), 4);
    }

    /**
     * Gets the float Z value.
     * @return floatZ
     */
    public float getFloatZ() {
        return Utils.fixedPointIntToFloatNBits(getZ(), 4);
    }

    @Override
    public String toRegularString() {
        return getX() + ", " + getY() + ", " + getZ();
    }

    @Override
    public String toString() {
        return toString0();
    }

    /**
     * Load a SVector with padding from a DataReader.
     * @param reader The data reader to read from.
     * @return vector
     */
    public static IVector readWithPadding(DataReader reader) {
        IVector vector = new IVector();
        vector.loadWithPadding(reader);
        return vector;
    }
}
