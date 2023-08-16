package net.highwayfrogs.editor.games.tgq.toc;

import lombok.Getter;
import net.highwayfrogs.editor.file.reader.DataReader;
import net.highwayfrogs.editor.file.writer.DataWriter;
import net.highwayfrogs.editor.games.tgq.TGQBinFile;
import net.highwayfrogs.editor.games.tgq.TGQChunkedFile;
import net.highwayfrogs.editor.games.tgq.TGQFile;
import net.highwayfrogs.editor.games.tgq.loading.kcLoadContext;
import net.highwayfrogs.editor.games.tgq.model.kcModel;
import net.highwayfrogs.editor.games.tgq.model.kcModelWrapper;

/**
 * A reference to a 3D model.
 * Created by Kneesnap on 6/28/2023.
 */
@Getter
public class kcCResourceModel extends kcCResource {
    private String fullPath; // Full file path to the real model file.

    public static final int FULL_PATH_SIZE = 260;
    public static final byte FULL_NAME_PADDING = (byte) 0xCD;

    public kcCResourceModel(TGQChunkedFile parentFile) {
        super(parentFile, KCResourceID.MODEL);
    }

    @Override
    public void load(DataReader reader) {
        super.load(reader);
        this.fullPath = reader.readTerminatedStringOfLength(FULL_PATH_SIZE);
    }

    @Override
    public void save(DataWriter writer) {
        super.save(writer);
        writer.writeTerminatedStringOfLength(this.fullPath, FULL_PATH_SIZE);
    }

    @Override
    public void afterLoad1(kcLoadContext context) {
        super.afterLoad1(context);
        // We must wait until afterLoad1() because the file object won't exist for files found later in the file if we don't.
        // But, this must run before afterLoad2() because that's when we start doing lookups based on file paths.
        TGQBinFile mainArchive = getMainArchive();
        if (mainArchive != null)
            mainArchive.applyFileName(this.fullPath, true);

        // Apply texture file names. This is done in afterLoad2() to wait for our own file path to be set.
        kcModelWrapper wrapper = getModelWrapper();
        if (wrapper != null)
            context.getMaterialLoadContext().applyLevelTextureFileNames(getParentFile(), this.fullPath, wrapper.getModel().getMaterials());
    }

    @Override
    public void afterLoad2(kcLoadContext context) {
        super.afterLoad2(context);
        // Now we'll resolve the textures for this model using the textures found in the chunked file.
        // We don't need to print a warning if the model doesn't exist, because the applyFileName() call would have already caught it.
        kcModelWrapper wrapper = getModelWrapper();
        if (wrapper != null)
            context.getMaterialLoadContext().resolveMaterialTexturesInChunk(getParentFile(), wrapper, wrapper.getModel().getMaterials());
    }

    /**
     * Gets the 3D this model stands in for.
     * @return model
     */
    public kcModel getModel() {
        TGQFile file = getOptionalFileByName(this.fullPath);
        return (file instanceof kcModelWrapper) ? ((kcModelWrapper) file).getModel() : null;
    }

    /**
     * Gets the wrapper around the 3D model.
     * @return modelWrapper
     */
    public kcModelWrapper getModelWrapper() {
        TGQFile file = getOptionalFileByName(this.fullPath);
        return (file instanceof kcModelWrapper) ? ((kcModelWrapper) file) : null;
    }
}