package net.highwayfrogs.editor.games.tgq.model;

import lombok.Getter;
import lombok.Setter;
import net.highwayfrogs.editor.Constants;
import net.highwayfrogs.editor.file.GameObject;
import net.highwayfrogs.editor.file.reader.DataReader;
import net.highwayfrogs.editor.file.writer.DataWriter;
import net.highwayfrogs.editor.games.tgq.TGQImageFile;
import net.highwayfrogs.editor.utils.Utils;

/**
 * Represents a material.
 * Created by Kneesnap on 6/22/2023.
 */
@Getter
public class kcMaterial extends GameObject {
    private String materialName;
    private String textureFileName;
    private int flags = 1; // TODO: The lower flags are cleared when the model is loaded. Presumably they are runtime only flags? Operation: value &= 0xfffffff7 Seems 0x01 is whether a texture name should be resolved. (kcImportMaterialTexture)
    private float xpVal = 0F;
    private float diffuseRed = 1F; // TODO: kcImportTextures seems to overwrite the values read from the file? Look into this later.
    private float diffuseGreen = 1F;
    private float diffuseBlue = 1F;
    private float diffuseAlpha = 1F;
    private float ambientRed = 1F;
    private float ambientGreen = 1F;
    private float ambientBlue = 1F;
    private float ambientAlpha = 1F;
    private float specularRed;
    private float specularGreen;
    private float specularBlue;
    private float specularAlpha;
    private float emissiveRed;
    private float emissiveGreen;
    private float emissiveBlue;
    private float emissiveAlpha = 1F;
    private float power = 1F;
    @Setter private transient TGQImageFile texture;

    private static final int NAME_SIZE = 32;
    private static final int FILENAME_SIZE = 32;

    @Override
    public void load(DataReader reader) {
        this.materialName = reader.readTerminatedStringOfLength(NAME_SIZE);
        this.textureFileName = reader.readTerminatedStringOfLength(FILENAME_SIZE);
        this.flags = reader.readInt();
        this.xpVal = reader.readFloat();
        this.diffuseRed = reader.readFloat();
        this.diffuseGreen = reader.readFloat();
        this.diffuseBlue = reader.readFloat();
        this.diffuseAlpha = reader.readFloat();
        this.ambientRed = reader.readFloat();
        this.ambientGreen = reader.readFloat();
        this.ambientBlue = reader.readFloat();
        this.ambientAlpha = reader.readFloat();
        this.specularRed = reader.readFloat();
        this.specularGreen = reader.readFloat();
        this.specularBlue = reader.readFloat();
        this.specularAlpha = reader.readFloat();
        this.emissiveRed = reader.readFloat();
        this.emissiveGreen = reader.readFloat();
        this.emissiveBlue = reader.readFloat();
        this.emissiveAlpha = reader.readFloat();
        this.power = reader.readFloat();

        int runtimeTexturePtr = reader.readInt();
        if (runtimeTexturePtr != 0) // Runtime value. TODO: This only seems to occur when loading map materials.
            System.out.println("NON-ZERO MATERIAL PTR!! " + Utils.toHexString(runtimeTexturePtr) + ", " + this.materialName + ", " + this.textureFileName + ", " + reader.getRemaining()); // This does not appear to be a hash, this value seems to sometimes be shared between seemingly unrelated textures for example between 'mushbot2.img' and 'crtA_sA.img'.
    }

    @Override
    public void save(DataWriter writer) {
        writer.writeTerminatedStringOfLength(this.materialName, NAME_SIZE);
        writer.writeTerminatedStringOfLength(this.textureFileName, FILENAME_SIZE);
        writer.writeInt(this.flags);
        writer.writeFloat(this.xpVal);
        writer.writeFloat(this.diffuseRed);
        writer.writeFloat(this.diffuseGreen);
        writer.writeFloat(this.diffuseBlue);
        writer.writeFloat(this.diffuseAlpha);
        writer.writeFloat(this.ambientRed);
        writer.writeFloat(this.ambientGreen);
        writer.writeFloat(this.ambientBlue);
        writer.writeFloat(this.ambientAlpha);
        writer.writeFloat(this.specularRed);
        writer.writeFloat(this.specularGreen);
        writer.writeFloat(this.specularBlue);
        writer.writeFloat(this.specularAlpha);
        writer.writeFloat(this.emissiveRed);
        writer.writeFloat(this.emissiveGreen);
        writer.writeFloat(this.emissiveBlue);
        writer.writeFloat(this.emissiveAlpha);
        writer.writeFloat(this.power);
        writer.writeInt(0); // Runtime value (texture pointer)
    }

    /**
     * Writes material information to the string builder.
     * @param builder           The builder to write material information to.
     * @param textureFilePrefix The prefix for the texture path, can be null.
     * @param includeAmbient    Whether ambient information should be included.
     * @param includeSpecular   Whether specular information should be included.
     */
    public void writeWavefrontObjMaterial(StringBuilder builder, String textureFilePrefix, boolean includeAmbient, boolean includeSpecular) {
        builder.append("newmtl ").append(this.materialName).append(Constants.NEWLINE);
        builder.append("Kd ").append(this.diffuseRed).append(' ')
                .append(this.diffuseGreen).append(' ')
                .append(this.diffuseBlue).append(Constants.NEWLINE);
        if (includeAmbient) {
            builder.append("Ka ").append(this.ambientRed).append(' ')
                    .append(this.ambientGreen).append(' ')
                    .append(this.ambientBlue).append(Constants.NEWLINE);
        }

        if (includeSpecular) {
            builder.append("Ks ").append(this.specularRed).append(' ')
                    .append(this.specularGreen).append(' ')
                    .append(this.specularBlue).append(Constants.NEWLINE);
        }

        // Transparency.
        builder.append("d ").append(this.diffuseAlpha).append(Constants.NEWLINE);

        // Diffuse texture map.
        builder.append("map_Kd ");
        if (textureFilePrefix != null)
            builder.append(textureFilePrefix);
        if (this.textureFileName != null)
            builder.append(Utils.stripExtension(this.textureFileName));
        builder.append(".png").append(Constants.NEWLINE);
    }
}