package net.highwayfrogs.editor.file.map.entity.data;

import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.util.converter.NumberStringConverter;
import lombok.Getter;
import lombok.Setter;
import net.highwayfrogs.editor.file.map.MAPFile;
import net.highwayfrogs.editor.file.map.path.PathInfo;
import net.highwayfrogs.editor.file.map.path.PathInfo.PathMotionType;
import net.highwayfrogs.editor.file.reader.DataReader;
import net.highwayfrogs.editor.file.writer.DataWriter;
import net.highwayfrogs.editor.gui.GUIEditorGrid;
import net.highwayfrogs.editor.gui.editor.map.manager.EntityManager;
import net.highwayfrogs.editor.utils.Utils;

import java.text.DecimalFormat;

/**
 * Base entity data which holds path data.
 * Created by Kneesnap on 1/20/2019.
 */
@Getter
@Setter
public class PathData extends EntityData {
    private PathInfo pathInfo = new PathInfo();
    // static thread to make sure only one is ever active at a time
    static Thread playPath = null;
    double lastValue = -1;

    @Override
    public void load(DataReader reader) {
        this.pathInfo.load(reader);
    }

    @Override
    public void save(DataWriter writer) {
        this.pathInfo.save(writer);
    }

    @Override
    public void addData(GUIEditorGrid editor) {
        MAPFile map = getParentEntity().getMap();
        editor.addIntegerField("Speed", getPathInfo().getSpeed(), getPathInfo()::setSpeed, null);

        final float distAlongPath = Utils.fixedPointIntToFloat4Bit(getPathInfo().getTotalPathDistance(map));
        final float totalPathDist = Utils.fixedPointIntToFloat4Bit(getPathInfo().getPath(map).getTotalLength());

        Slider travDistSlider = editor.addDoubleSlider("Travel Distance:", distAlongPath, newValue -> getPathInfo().setTotalPathDistance(getParentEntity().getMap(), Utils.floatToFixedPointInt4Bit(newValue.floatValue())), 0.0, totalPathDist);
        TextField travDistText = editor.addFloatField("", distAlongPath, newValue -> getPathInfo().setTotalPathDistance(getParentEntity().getMap(), Utils.floatToFixedPointInt4Bit(newValue)), newValue -> !((newValue < 0.0f) || (newValue > totalPathDist)));
        travDistText.textProperty().bindBidirectional(travDistSlider.valueProperty(), new NumberStringConverter(new DecimalFormat("####0.00")));

        TextField txtFieldMaxTravel = editor.addFloatField("(Max. Travel):", totalPathDist);
        txtFieldMaxTravel.setEditable(false);
        txtFieldMaxTravel.setDisable(true);

        // Motion Data:
        for (PathMotionType type : PathMotionType.values())
            if (type.isAllowEdit())
                editor.addCheckBox(Utils.capitalize(type.name()), getPathInfo().testFlag(type), newState -> getPathInfo().setFlag(type, newState));
    }

    @Override
    public void addData(EntityManager manager, GUIEditorGrid editor) {
        MAPFile map = getParentEntity().getMap();
        int pathId = getPathInfo().getPathId();
        if (pathId < 0 || pathId >= map.getPaths().size()) { // Invalid path! Show this as a text box.
            editor.addIntegerField("Path ID", pathId, getPathInfo()::setPathId, null);
        } else { // Otherwise, show it as a selection box!
            editor.addBoldLabelButton("Path #" + pathId, "Select Path", 25, () ->
                    manager.getController().getPathManager().promptPath((path, segment, segDistance) -> {
                        getPathInfo().setPath(manager.getMap(), path, segment);
                        getPathInfo().setSegmentDistance(segDistance);
                        manager.updateEntities();
                        manager.showEntityInfo(getParentEntity()); // Update the entity editor display, update path slider, etc.
                    }, null));
        }

        // The play button will move the entity across the chosen path to simulate how it might look in-game
        // This is a shaky implementation, given it runs on a basic, static thread, and uses busy waiting
        // It also sometimes seems to freeze unless you hover your mouse over the preview window
        lastValue = Utils.fixedPointIntToFloat4Bit(getPathInfo().getTotalPathDistance(map));
        playPath = null;
        Button playButton = editor.addButton("Play", () -> {});
        playButton.setOnMouseClicked(evt -> {
            if (playButton.getText().equals("Play")) {
                playButton.setText("Stop");
                if (playPath == null || !playPath.isAlive()) {
                    playPath = new Thread(() -> {
                        try {
                            double val = lastValue;
                            while (playPath != null && playPath.isAlive()) {
                                val += (getPathInfo().getSpeed() * 1.0 / 50);
                                if (val > Utils.fixedPointIntToFloat4Bit(getPathInfo().getPath(getParentEntity().getMap()).getTotalLength())) {
                                    val = 0;
                                }
                                //System.out.println(val);
                                getPathInfo().setTotalPathDistance(getParentEntity().getMap(), Utils.floatToFixedPointInt4Bit((float) val));
                                manager.updatePosition(getParentEntity());
                                Thread.sleep(10);
                            }
                        } catch (Exception e) {
                            // Only one thread at a time
                        }
                        getPathInfo().setTotalPathDistance(getParentEntity().getMap(), Utils.floatToFixedPointInt4Bit((float) lastValue));
                        manager.updatePosition(getParentEntity());
                    });
                    playPath.start();
                }
            } else {
                playButton.setText("Play");
                playPath = null;
            }
        });

        super.addData(manager, editor); // Path ID comes before the rest.
    }
}
