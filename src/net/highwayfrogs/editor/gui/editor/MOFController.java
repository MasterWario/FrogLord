package net.highwayfrogs.editor.gui.editor;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.*;
import javafx.scene.control.Accordion;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.SneakyThrows;
import net.highwayfrogs.editor.Utils;
import net.highwayfrogs.editor.file.map.view.TextureMap;
import net.highwayfrogs.editor.file.mof.MOFFile;
import net.highwayfrogs.editor.file.mof.view.MOFMesh;
import net.highwayfrogs.editor.gui.GUIMain;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controls the MOF editor GUI.
 * Created by Kneesnap on 2/13/2019.
 */
@Getter
public class MOFController extends EditorController<MOFFile> {
    private double oldMouseX;
    private double oldMouseY;
    private double mouseX;
    private double mouseY;

    private MOFUIController uiController;
    private PerspectiveCamera camera;
    private Scene mofScene;
    private MOFMesh mofMesh;
    private Group root3D;
    private Rotate rotX;
    private Rotate rotY;
    private Rotate rotZ;

    @FXML
    private void onMofButtonClicked(ActionEvent event) {
        TextureMap textureMap = TextureMap.newTextureMap(getFile());
        setupMofViewer(GUIMain.MAIN_STAGE, textureMap);
    }

    @SneakyThrows
    private void setupMofViewer(Stage stageToOverride, TextureMap texMap) {
        this.mofMesh = new MOFMesh(getFile(), texMap);

        // Create and setup material properties for rendering the level, entity icons and bounding boxes.
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseMap(Utils.toFXImage(texMap.getImage(), true));

        // Create mesh view and initialise with xyz rotation transforms, materials and initial face culling policy.
        MeshView meshView = new MeshView(getMofMesh());

        this.rotX = new Rotate(0, Rotate.X_AXIS);
        this.rotY = new Rotate(0, Rotate.Y_AXIS);
        this.rotZ = new Rotate(0, Rotate.Z_AXIS);
        meshView.getTransforms().addAll(rotX, rotY, rotZ);

        meshView.setMaterial(material);
        meshView.setCullFace(CullFace.NONE);

        // Setup a perspective camera through which the 3D view is realised.
        this.camera = new PerspectiveCamera(true);

        // Load FXML for UI layout.
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/javafx/mof-view.fxml"));
        Parent loadRoot = fxmlLoader.load();
        this.uiController = fxmlLoader.getController();

        // Create the 3D elements and use them within a subscene.
        this.root3D = new Group(this.camera, meshView);
        SubScene subScene3D = new SubScene(root3D, stageToOverride.getScene().getWidth() - uiController.uiRootPaneWidth(), stageToOverride.getScene().getHeight(), true, SceneAntialiasing.BALANCED);

        // Setup the UI layout.
        BorderPane uiPane = new BorderPane();
        uiPane.setLeft(loadRoot);
        uiPane.setCenter(subScene3D);


        // Create and set the scene.
        mofScene = new Scene(uiPane);
        Scene defaultScene = Utils.setSceneKeepPosition(stageToOverride, mofScene);

        // Handle scaling of SubScene on stage resizing.
        mofScene.widthProperty().addListener((observable, old, newVal) -> subScene3D.setWidth(newVal.doubleValue() - uiController.uiRootPaneWidth()));
        subScene3D.heightProperty().bind(mofScene.heightProperty());

        // Input (key) event processing.
        mofScene.setOnKeyPressed(event -> {
            // Exit the viewer.
            if (event.getCode() == KeyCode.ESCAPE)
                Utils.setSceneKeepPosition(stageToOverride, defaultScene);

            // Toggle wireframe mode.
            if (event.getCode() == KeyCode.X)
                meshView.setDrawMode(meshView.getDrawMode() == DrawMode.FILL ? DrawMode.LINE : DrawMode.FILL);
        });

        mofScene.setOnScroll(evt -> camera.setTranslateZ(camera.getTranslateZ() + (evt.getDeltaY() * MapUIController.getSpeedModifier(evt, MapUIController.getPropertyScrollSpeed()))));

        mofScene.setOnMousePressed(e -> {
            mouseX = oldMouseX = e.getSceneX();
            mouseY = oldMouseY = e.getSceneY();
        });

        mofScene.setOnMouseDragged(e -> {
            oldMouseX = mouseX;
            oldMouseY = mouseY;
            mouseX = e.getSceneX();
            mouseY = e.getSceneY();
            double mouseXDelta = (mouseX - oldMouseX);
            double mouseYDelta = (mouseY - oldMouseY);

            if (e.isPrimaryButtonDown()) {
                rotX.setAngle(rotX.getAngle() + (mouseYDelta * MapUIController.getSpeedModifier(e, MapUIController.getPropertyRotationSpeed()))); // Rotate the object.
                rotY.setAngle(rotY.getAngle() - (mouseXDelta * MapUIController.getSpeedModifier(e, MapUIController.getPropertyRotationSpeed())));
            } else if (e.isMiddleButtonDown()) {
                camera.setTranslateX(camera.getTranslateX() - (mouseXDelta * MapUIController.getSpeedModifier(e, MapUIController.getPropertyTranslateSpeed()))); // Move the camera.
                camera.setTranslateY(camera.getTranslateY() - (mouseYDelta * MapUIController.getSpeedModifier(e, MapUIController.getPropertyTranslateSpeed())));
            }
        });

        // Set the initial camera position to somewhere sensible :)
        //  - Maybe calculate this based on some metric rather than supplying arbitrary values?
        camera.setTranslateZ(-1000.0);
        camera.setTranslateY(-100.0);
    }

    public static final class MOFUIController implements Initializable {
        // Baseline UI components
        @FXML private AnchorPane anchorPaneUIRoot;
        @FXML private Accordion accordionLeft;

        @Override
        public void initialize(URL location, ResourceBundle resources) {

        }

        /**
         * Get the root pane width.
         */
        public double uiRootPaneWidth() {
            return anchorPaneUIRoot.getPrefWidth();
        }
    }
}