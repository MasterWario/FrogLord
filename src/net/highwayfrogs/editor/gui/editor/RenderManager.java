package net.highwayfrogs.editor.gui.editor;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Translate;
import lombok.Getter;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Manages rendering and rendering resources / assets.
 * Created by AndyEder on 2/21/2019.
 */
@Getter
public class RenderManager {
    private Group root = null;
    private Map<String, List<Node>> displayListCache = new HashMap<>();

    //>>

    public RenderManager() {
    }

    /**
     * Sets the root scene graph render node.
     * @param root The root scene graph node.
     */
    public void setRenderRoot(Group root) {
        this.root = root;
    }

    /**
     * Sets the root scene graph render node.
     * @return The root scene graph node.
     */
    public Group getRenderRoot() {
        return this.root;
    }

    /**
     * Check to see if the specified display list with the specified ID already exists in the cache.
     * @param listID The ID (key) for the display list.
     * @return True if the specified list exists, else false
     */
    public boolean displayListExists(String listID) {
        return displayListCache.containsKey(listID);
    }

    /**
     * If a display list is missing, add it.
     * @param listID The list id to attempt adding.
     */
    public void addMissingDisplayList(String listID) {
        if (!displayListCache.containsKey(listID))
            addDisplayList(listID);
    }

    /**
     * Adds a new display list with the specified ID.
     * @param listID The ID (key) for the display list.
     */
    public void addDisplayList(String listID) {
        if (!displayListCache.containsKey(listID)) {
            // Create a new list keyed on the provided ID
            displayListCache.put(listID, new LinkedList<>());
        } else {
            // A list already exists keyed on the provided ID
            throw new RuntimeException("RenderManager::addDisplayList() - " + listID + " already exists!");
        }
    }

    /**
     * Removes an existing display list with the specified ID.
     * @param listID The ID (key) for the display list.
     */
    public void removeDisplayList(String listID) {
        if (displayListCache.containsKey(listID)) {
            final List<Node> items = displayListCache.get(listID);

            // Remove items from display and clear the list
            this.root.getChildren().removeAll(items);
            items.clear();

            // Remove from the cache
            displayListCache.remove(listID);
        } else {
            // The specified list does not exist
            throw new RuntimeException("RenderManager::removeDisplayList() - " + listID + " does not exist!");
        }
    }

    /**
     * Clears and removes all display lists.
     */
    public void removeAllDisplayLists() {
        // Clear items from all display lists
        this.clearAllDisplayLists();

        // Clear the cache
        displayListCache.clear();
    }

    /**
     * Clears the specified display list.
     * @param listID The ID (key) for the display list.
     */
    public void clearDisplayList(String listID) {
        if (!displayListCache.containsKey(listID))
            return;

        final List<Node> items = displayListCache.get(listID);

        // Remove items from display and clear the list
        this.root.getChildren().removeAll(items);
        items.clear();
        
    }

    /**
     * Clears all display lists.
     */
    public void clearAllDisplayLists() {
        // Clear items from each display list
        displayListCache.forEach((key, items) ->
        {
            // Remove items from display
            this.root.getChildren().removeAll(items);
            items.clear();
        });
    }

    /**
     * Shows or hides all items in the specified display list.
     * @param listID The ID of the display list.
     * @param show   True to show items, false to hide.
     */
    public void showDisplayList(String listID, boolean show) {
        if (displayListCache.containsKey(listID)) {
            displayListCache.get(listID).forEach(node -> node.setVisible(show));
        } else {
            throw new RuntimeException("RenderManager::showDisplayList() - " + listID + " does not exist!");
        }
    }

    /**
     * Adds a bounding box to the specified display list.
     * @param listID The ID of the display list.
     * @param node   The node to add.
     */
    public void addNode(String listID, Node node) {
        if (displayListCache.containsKey(listID)) {
            displayListCache.get(listID).add(node);
            this.root.getChildren().add(node);
        } else {
            throw new RuntimeException("RenderManager::addNode() - " + listID + " does not exist!");
        }
    }

    /**
     * Removes a bounding box from the specified display list.
     * @param listID The ID of the display list.
     * @param bbox   The bounding box to remove.
     */
    public void removeBoundingBox(String listID, Box bbox) {
        if (displayListCache.containsKey(listID)) {
            this.root.getChildren().remove(bbox);
            displayListCache.get(listID).remove(bbox);
        } else {
            throw new RuntimeException("RenderManager::removeBoundingBox() - " + listID + " does not exist!");
        }
    }

    /**
     * Adds an axis-aligned bounding box.
     * @param listID The display list ID.
     * @param minX   The minimum x-coordinate.
     * @param minY   The minimum y-coordinate.
     * @param minZ   The minimum z-coordinate.
     * @param maxX   The maximum x-coordinate.
     * @param maxY   The maximum y-coordinate.
     * @param maxZ   The maximum z-coordinate.
     * @return The newly created/added bounding box
     */
    public Box addBoundingBoxFromMinMax(String listID, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, PhongMaterial material, boolean useWireframe) {
        if (displayListCache.containsKey(listID)) {
            final double x0 = Math.min(minX, maxX);
            final double x1 = Math.max(minX, maxX);
            final double y0 = Math.min(minY, maxY);
            final double y1 = Math.max(minY, maxY);
            final double z0 = Math.min(minZ, maxZ);
            final double z1 = Math.max(minZ, maxZ);

            final double x = (x0 + x1) * 0.5;
            final double y = (y0 + y1) * 0.5;
            final double z = (z0 + z1) * 0.5;

            final double w = (x1 - x0);
            final double h = (y1 - y0);
            final double d = (z1 - z0);

            return this.addBoundingBoxCenteredWithDimensions(listID, x, y, z, w, h, d, material, useWireframe);
        } else {
            throw new RuntimeException("RenderManager::addBoundingBoxFromMinMax() - " + listID + " does not exist!");
        }
    }

    /**
     * Adds an axis-aligned bounding box.
     * @param listID The display list ID.
     * @param x      The x-coordinate defining the center of the box.
     * @param y      The y-coordinate defining the center of the box.
     * @param z      The z-coordinate defining the center of the box.
     * @param width  The width (along x-axis).
     * @param height The height (along y-axis).
     * @param depth  The depth (along z-axis).
     * @return The newly created/added bounding box
     */
    public Box addBoundingBoxCenteredWithDimensions(String listID, double x, double y, double z, double width, double height, double depth, PhongMaterial material, boolean useWireframe) {
        if (displayListCache.containsKey(listID)) {
            Box axisAlignedBoundingBox = new Box(width, height, depth);

            axisAlignedBoundingBox.setMaterial(material);
            axisAlignedBoundingBox.setDrawMode(useWireframe ? DrawMode.LINE : DrawMode.FILL);
            axisAlignedBoundingBox.setCullFace(CullFace.BACK);
            axisAlignedBoundingBox.getTransforms().addAll(new Translate(x, y, z));
            axisAlignedBoundingBox.setMouseTransparent(useWireframe);

            displayListCache.get(listID).add(axisAlignedBoundingBox);
            this.root.getChildren().add(axisAlignedBoundingBox);

            return axisAlignedBoundingBox;
        } else {
            throw new RuntimeException("RenderManager::addBoundingBoxCenteredWithDimensions() - " + listID + " does not exist!");
        }
    }

    /**
     * Adds a sphere.
     * @param listID       The display list ID.
     * @param x0           The x-coordinate defining the center of the sphere.
     * @param y0           The y-coordinate defining the center of the sphere.
     * @param z0           The z-coordinate defining the center of the sphere.
     * @param radius       The radius of the cylinder.
     * @param material     The material used to render the sphere.
     * @param useWireframe Whether or not to display the sphere as a wireframe.
     * @return The newly created/added sphere
     */
    public Sphere addSphere(String listID, double x0, double y0, double z0, double radius, PhongMaterial material, boolean useWireframe) {
        if (displayListCache.containsKey(listID)) {
            Sphere sph0 = new Sphere(radius, 8);
            sph0.setMaterial(material);
            sph0.setDrawMode(useWireframe ? DrawMode.LINE : DrawMode.FILL);
            sph0.setCullFace(CullFace.BACK);
            sph0.setMouseTransparent(useWireframe);
            sph0.getTransforms().addAll(new Translate(x0, y0, z0));

            displayListCache.get(listID).add(sph0);
            this.root.getChildren().add(sph0);

            return sph0;
        } else {
            throw new RuntimeException("RenderManager::addSphere() - " + listID + " does not exist!");
        }
    }

    /**
     * Displays som basic stats / debug information.
     */
    public void showDisplayListStats() {
        System.out.println("[ RenderManager - Display Lists ]");
        displayListCache.forEach((key, val) -> System.out.println("displayListCache[" + key + "] contains " + val.size() + " items"));
    }
}
