package com.example.visualize;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.ArrayList;
import java.util.List;

public class ARActivity extends AppCompatActivity {

    private ArFragment arFragment;
    private FloatingActionButton fab;

    private String modelUrl;

    private boolean isHitting = false;
    private boolean isTracking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);
        fab = (FloatingActionButton) findViewById(R.id.floatingActionButton);

        Intent intent = getIntent();
        modelUrl = intent.getStringExtra("MODEL_URL");

        // Modify update event listener
        // Call onUpdate function before processing every frame
        arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            arFragment.onUpdate(frameTime);
            onUpdate();
        });

        fab.setOnClickListener(view -> {
                addObject(
                        Uri.parse(modelUrl)
                );
        });

        showFab(false);
    }


    // Show/Hide the FloatingActionButton
    private void showFab(boolean show) {
        if(show) {
            fab.show();
        } else {
            fab.hide();
        }
    }

    // Custom function to update tracking state
    private void onUpdate() {
        updateTracking();
        // Check if the devices gaze is hitting a plane detected by ARCore
        if(isTracking) {
            boolean hitTestChanged = updateHitTest();
            if(hitTestChanged) {
                showFab(isHitting);
            }
        }
    }

    // Performs frame.HitTest and returns if a hit is detected
    private boolean updateHitTest() {
        Frame frame = arFragment.getArSceneView().getArFrame();
        Point point = getScreenCenter();
        List<HitResult> hits;
        boolean wasHitting = isHitting;
        isHitting = false;
        if(frame != null) {
            hits = frame.hitTest(point.x, point.y);
            for(HitResult hit:hits) {
                Trackable trackable = hit.getTrackable();
                if(
                        trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                ) {
                    isHitting = true;
                    break;
                }
            }
        }

        return wasHitting != isHitting;
    }

    // Makes use of ARCore's camera state and returns true if the tracking state has changed
    private boolean updateTracking() {
        Frame frame = arFragment.getArSceneView().getArFrame();
        boolean wasTracking = isTracking;
        try {
            isTracking = frame.getCamera().getTrackingState() == TrackingState.TRACKING;
        } catch(NullPointerException e) {
            return false;
        }
        return isTracking != wasTracking;
    }

    // Simply returns the center of the screen
    private Point getScreenCenter() {
        View view = findViewById(R.id.container);
        return new Point(view.getWidth() / 2, view.getHeight() / 2);
    }

    private void addObject(Uri model) {
        Frame frame = arFragment.getArSceneView().getArFrame();
        Point point = getScreenCenter();
        List<HitResult> hits;

        if(frame != null) {
            hits = frame.hitTest(point.x, point.y);
            for(HitResult hit:hits) {
                Trackable trackable = hit.getTrackable();
                if(
                        trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                ) {
                    placeOject(arFragment, hit.createAnchor(), model);
                    break;
                }
            }
        }
    }

    private void placeOject(ArFragment fragment, Anchor anchor, Uri model) {
        ModelRenderable.builder()
                .setSource(fragment.getContext(), RenderableSource.builder().setSource(
                        fragment.getContext(),
                        model,
                        RenderableSource.SourceType.GLTF2).build()
                )
                .setRegistryId(model)
                .build()
                .thenAccept(renderable -> addNodeToScene(fragment, anchor, renderable))
                .exceptionally(throwable -> {
                        Toast.makeText(
                                getApplicationContext(),
                                "Could not fetch model from source",
                                Toast.LENGTH_SHORT
                        ).show();
                        return null;
                });
    }

    private void addNodeToScene(ArFragment fragment, Anchor anchor, ModelRenderable renderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        // TransformableNode means the user to move, scale and rotate the model
        TransformableNode transformableNode = new TransformableNode(fragment.getTransformationSystem());
        transformableNode.setRenderable(renderable);
        transformableNode.setParent(anchorNode);
        fragment.getArSceneView().getScene().addChild(transformableNode);
        transformableNode.select();
    }
}
