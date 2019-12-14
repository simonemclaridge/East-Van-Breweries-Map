/**
 * Copyright 2019 Esri
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.mycompany.app;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.IdentifyLayerResult;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalItem;
import javafx.application.Application;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;

import java.util.stream.Collectors;

public class App extends Application {

    private MapView mapView;

    public static void main(String[] args) {

        Application.launch(args);
    }

    //creates a portal to establish a connection to AGO to access the East Van breweries data
    private void setupPortalItem() {
        String portalItemId = "317b5f03d5de4f368fb802fe32d15dfa";
        Portal portal = new Portal("https://www.arcgis.com");

        //creates a PortalItem using the portal and the unique ID number
        PortalItem portalItem = new PortalItem(portal, portalItemId);

        //waits for the portal item to load and checks the load status
        portalItem.addDoneLoadingListener(() -> {
            if (portalItem.getLoadStatus() == LoadStatus.LOADED) {
                addBreweriesLayer(portalItem);
            } else {
                new Alert(AlertType.ERROR, "Portal Item: " + portalItem.getLoadError().getMessage()).show();
            }
        });
        portalItem.loadAsync();

    }

    //creates a feature layer from a portal id and layer id from East Van feature layer
    private void addBreweriesLayer(PortalItem portalItem) {
        int layerId = 0;
        FeatureLayer layer = new FeatureLayer(portalItem, layerId);

        //creates a Light Gray Canvas base map for the map
        Basemap basemap = Basemap.createLightGrayCanvasVector();
        ArcGISMap map = new ArcGISMap(basemap);


        //wait for layer to load then add the layer to the map and set the viewpoint
        //to display all of the features of the layer
        layer.addDoneLoadingListener(() -> {
            if (layer.getLoadStatus() == LoadStatus.LOADED) {

                //add to the map as an operational layer
                //displays the features above the base map
                mapView.setMap(map);
                mapView.getMap().getOperationalLayers().add(layer);
                mapView.setViewpoint(new Viewpoint(layer.getFullExtent()));
            } else {
                new Alert(AlertType.ERROR, "Feature Layer: " + layer.getLoadError().getMessage()).show();
            }
        });
        layer.loadAsync();

        //listen to the mouse clicked event on the map view
        mapView.setOnMouseClicked((e) -> {

            // create a point from where the user clicked
            Point2D screenPoint = new Point2D(e.getX(), e.getY());

            //selects features that have been clicked
            selectFeature(e, layer, screenPoint);

            // creates a callout where the user clicked
            createGeoCallOut(e, screenPoint);
        });
    }

    public void selectFeature(MouseEvent e, FeatureLayer layer, Point2D screenPoint) {

        // check for primary or secondary mouse click
        if (e.isStillSincePress() && e.getButton() == MouseButton.PRIMARY) {
            // clear previous results
            layer.clearSelection();

            // was the main button pressed?
            if (e.getButton() == MouseButton.PRIMARY) {
                // get the screen point where the user clicked or tapped

                // identify the clicked features
                final ListenableFuture<IdentifyLayerResult> results = mapView.identifyLayerAsync(layer, screenPoint, 10,
                        false, 10);
                results.addDoneListener(() -> {
                    try {

                        IdentifyLayerResult Layer = results.get();

                        // search the layers for identified features
                        List<Feature> features = Layer.getElements().stream()
                                .filter(geoElement -> geoElement instanceof Feature)
                                .map(g -> (Feature) g)
                                .collect(Collectors.toList());

                        // select features
                        layer.selectFeatures(features);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            }
        }
    }


    public void createGeoCallOut(MouseEvent e, Point2D screenPoint) {


        // get the map view's callout
        Callout callout = mapView.getCallout();
        if(callout.isVisible()){

            callout.dismiss();
        }

        // check that the primary mouse button was clicked and user is not
        // panning
        if (e.isStillSincePress() && e.getButton() == MouseButton.PRIMARY) {

            // was the main button pressed?
            if (e.getButton() == MouseButton.PRIMARY) {

                // create a map point from a point
                Point mapPoint = mapView.screenToLocation(screenPoint);

                // callout show and hide animation duration
                final Duration DURATION = new Duration(0);

                if (!callout.isVisible()) {
                    // set the callout's details
                    callout.setTitle("Location");
                    callout.setDetail(String.format("x: %.2f, y: %.2f", mapPoint.getX(), mapPoint.getY()));

                    // show the callout where the user clicked
                    callout.showCalloutAt(mapPoint, DURATION);
                } else {
                    // hide the callout
                    callout.dismiss();
                }
            }
        }
    }


    @Override
    public void start(Stage stage) {


        // set the title and size of the stage and show it
        stage.setTitle("East Van Breweries");
        stage.setWidth(800);
        stage.setHeight(700);
        stage.show();

        // create a JavaFX scene with a stack pane as the root node and add it to the scene
        StackPane stackPane = new StackPane();
        Scene scene = new Scene(stackPane);
        stage.setScene(scene);

        // create a MapView to display the map and add it to the stack pane
        mapView = new MapView();
        stackPane.getChildren().add(mapView);

        //adds a layer from AGO to the map
        setupPortalItem();

    }

    /**
     * Stops and releases all resources used in application.
     */
    @Override
    public void stop() {

        if (mapView != null) {
            mapView.dispose();
        }
    }
}
