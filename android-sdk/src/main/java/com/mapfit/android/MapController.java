package com.mapfit.android;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
import android.os.Handler;
import android.support.annotation.Keep;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import com.mapfit.android.annotations.Annotation;
import com.mapfit.android.annotations.Marker;
import com.mapfit.android.annotations.MarkerOptions;
import com.mapfit.android.annotations.OnAnnotationClickListener;
import com.mapfit.android.annotations.Polygon;
import com.mapfit.android.annotations.PolygonOptions;
import com.mapfit.android.annotations.Polyline;
import com.mapfit.android.annotations.PolylineOptions;
import com.mapfit.android.geometry.LatLng;
import com.mapfit.android.geometry.LatLngBounds;
import com.mapfit.android.utils.DebugUtils;
import com.mapfit.tetragon.FontFileParser;
import com.mapfit.tetragon.HttpHandler;
import com.mapfit.tetragon.LabelPickResult;
import com.mapfit.tetragon.MarkerPickResult;
import com.mapfit.tetragon.SceneError;
import com.mapfit.tetragon.SceneUpdate;
import com.mapfit.tetragon.TouchInput;
import com.mapfit.tetragon.TouchInput.Gestures;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import kotlin.Pair;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * {@code MapController} is the main class for interacting with a Tangram map.
 */
public class MapController implements Renderer {

    /**
     * Options for interpolating map parameters
     */
    @Keep
    public enum EaseType {
        LINEAR,
        CUBIC_IN,
        CUBIC_OUT,
        CUBIC_IN_OUT,
        QUART_IN,
        QUART_OUT,
        QUART_IN_OUT,
        QUINT_IN,
        QUINT_OUT,
        QUINT_IN_OUT,
        SINE_IN,
        SINE_OUT,
        SINE_IN_OUT,
        EXP_IN,
        EXP_OUT,
        EXP_IN_OUT
    }

    /**
     * Options for changing the appearance of 3D geometry
     */
    public enum CameraType {
        PERSPECTIVE,
        ISOMETRIC,
        FLAT,
    }

    /**
     * Options representing an error generated after from the map controller
     */
    public enum Error {
        NONE,
        SCENE_UPDATE_PATH_NOT_FOUND,
        SCENE_UPDATE_PATH_YAML_SYNTAX_ERROR,
        SCENE_UPDATE_VALUE_YAML_SYNTAX_ERROR,
        NO_VALID_SCENE,
    }

    public EaseType DEFAULT_EASE_TYPE = EaseType.QUART_IN_OUT;

    /**
     * Options for enabling debug rendering features
     */
    public enum DebugFlag {
        FREEZE_TILES,
        PROXY_COLORS,
        TILE_BOUNDS,
        TILE_INFOS,
        LABELS,
        TANGRAM_INFOS,
        DRAW_ALL_LABELS,
        TANGRAM_STATS,
        SELECTION_BUFFER,
    }

    public boolean contains(@NotNull Annotation annotation) {
        return annotation instanceof Marker && markers.containsValue(annotation) ||
                annotation instanceof Polyline && polylines.containsValue(annotation) ||
                annotation instanceof Polygon && polygons.containsValue(annotation);
    }

    /**
     * Interface for a callback to receive information about features picked from the map
     * Triggered after a call of {@link #pickFeature(float, float)}
     * Listener should be set with {@link #setFeaturePickListener()}
     * The callback will be run on the main (UI) thread.
     */
    @Keep
    public interface FeaturePickListener {
        /**
         * Receive information about features found in a call to {@link #pickFeature(float, float)}
         *
         * @param properties A mapping of string keys to string or number values
         * @param positionX  The horizontal screen coordginate of the picked location
         * @param positionY  The vertical screen coordinate of the picked location
         */
        void onFeaturePick(Map<String, String> properties, float positionX, float positionY);
    }

    /**
     * Interface for a callback to receive information about labels picked from the map
     * Triggered after a call of {@link #pickLabel(float, float)}
     * Listener should be set with {@link #setLabelPickListener(LabelPickListener)}
     * The callback will be run on the main (UI) thread.
     */
    @Keep
    public interface LabelPickListener {
        /**
         * Receive information about labels found in a call to {@link #pickLabel(float, float)}
         *
         * @param labelPickResult The {@link LabelPickResult} that has been selected
         * @param positionX       The horizontal screen coordinate of the picked location
         * @param positionY       The vertical screen coordinate of the picked location
         */
        void onLabelPick(LabelPickResult labelPickResult, float positionX, float positionY);
    }

    /**
     * Interface for a callback to receive the picked {@link Marker}
     * Triggered after a call of {@link #pickMarker(float, float)}
     * The callback will be run on the main (UI) thread.
     */
    @Keep
    public interface MarkerPickListener {
        /**
         * Receive information about marker found in a call to {@link #pickMarker(float, float)}
         *
         * @param markerPickResult The {@link MarkerPickResult} the marker that has been selected
         * @param positionX        The horizontal screen coordinate of the picked location
         * @param positionY        The vertical screen coordinate of the picked location
         */
        void onMarkerPick(MarkerPickResult markerPickResult, float positionX, float positionY);
    }

    public interface ViewCompleteListener {
        /**
         * Called on the UI thread at the end of whenever the view is stationary, fully loaded, and
         * no animations are running.
         */
        void onViewComplete();
    }

    /**
     * Interface for listening to scene load status information.
     * Triggered after a call of {@link #updateSceneAsync(List< SceneUpdate >)} or
     * {@link #loadSceneFileAsync(String, List< SceneUpdate >)} or {@link #loadSceneFile(String, List< SceneUpdate >)}
     * Listener should be set with {@link #setSceneLoadListener(SceneLoadListener)}
     * The callbacks will be run on the main (UI) thread.
     */
    @Keep
    public interface SceneLoadListener {
        /**
         * Received when a scene load or update finishes. If sceneError is not null then the operation did not succeed.
         *
         * @param sceneId    The identifier returned by {@link #updateSceneAsync(List< SceneUpdate >)} or
         *                   {@link #loadSceneFileAsync(String, List< SceneUpdate >)}.
         * @param sceneError A {@link SceneError} holding error information, or null if no error occurred.
         */
        void onSceneReady(int sceneId, SceneError sceneError);
    }

    /**
     * Callback for {@link #captureFrame(FrameCaptureCallback, boolean) }
     */
    public interface FrameCaptureCallback {
        /**
         * Called on the render-thread when a frame was captured.
         */
        void onCaptured(Bitmap bitmap);
    }

    /**
     * Capture MapView as Bitmap.
     *
     * @param waitForCompleteView Delay the capture until the view is fully loaded and
     *                            no ease- or label-animation is running.
     */
    public void captureFrame(FrameCaptureCallback callback, boolean waitForCompleteView) {
        frameCaptureCallback = callback;
        frameCaptureAwaitCompleteView = waitForCompleteView;
        requestRender();
    }

    public Bitmap capture() {
        int w = mapView.getWidth();
        int h = mapView.getHeight();

        int b[] = new int[w * h];
        int bt[] = new int[w * h];

        nativeCaptureSnapshot(mapPointer, b);

        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                int pix = b[i * w + j];
                int pb = (pix >> 16) & 0xff;
                int pr = (pix << 16) & 0x00ff0000;
                int pix1 = (pix & 0xff00ff00) | pr | pb;
                bt[(h - i - 1) * w + j] = pix1;
            }
        }

        return Bitmap.createBitmap(bt, w, h, Bitmap.Config.ARGB_8888);
    }

    /**
     * Construct a MapController using a custom scene file
     *
     * @param view GLSurfaceView for the map display; input events from this
     *             view will be handled by the MapController's TouchInput gesture detector.
     *             It also provides the Context in which the map will function; the asset
     *             bundle for this activity must contain all the local files that the map
     *             will need.
     */
    MapController(GLSurfaceView view) {

        // Set up MapView
        mapView = view;
        view.setRenderer(this);
        view.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        view.setPreserveEGLContextOnPause(true);

        setRenderMode(1);

        // Set a default HTTPHandler
        httpHandler = new HttpHandler();

        touchInput = new TouchInput(view.getContext());
        view.setOnTouchListener(touchInput);

        setPanResponder(null);
        setScaleResponder(null);
        setRotateResponder(null);
        setShoveResponder(null);

        touchInput.setSimultaneousDetectionAllowed(Gestures.SHOVE, Gestures.ROTATE, false);
        touchInput.setSimultaneousDetectionAllowed(Gestures.ROTATE, Gestures.SHOVE, false);
        touchInput.setSimultaneousDetectionAllowed(Gestures.SHOVE, Gestures.SCALE, false);
        touchInput.setSimultaneousDetectionAllowed(Gestures.SHOVE, Gestures.PAN, false);
        touchInput.setSimultaneousDetectionAllowed(Gestures.SCALE, Gestures.LONG_PRESS, false);

        uiThreadHandler = new Handler(view.getContext().getMainLooper());
    }

    /**
     * Initialize native Tangram component. This must be called before any use
     * of the MapController!
     * This function is separated from MapController constructor to allow
     * initialization and loading of the Scene on a background thread.
     */
    void init() {
        // Get configuration info from application
        displayMetrics = mapView.getContext().getResources().getDisplayMetrics();
        assetManager = mapView.getContext().getAssets();

        fontFileParser = new FontFileParser();

        // Parse font file description
        fontFileParser.parse();

        mapPointer = nativeInit(this, assetManager);
        if (mapPointer <= 0) {
            throw new RuntimeException("Unable to create a native Map object! There may be insufficient memory available.");
        }
    }

    private static final String POLYGON_LAYER_NAME = "mz_default_polygon";
    private static final String POLYLINE_LAYER_NAME = "mz_default_line";

    void dispose() {
        // Disposing native resources involves GL calls, so we need to run on the GL thread.
        queueEvent(new Runnable() {
            @Override
            public void run() {
                // Dispose each data sources by first removing it from the HashMap values and then
                // calling remove(), so that we don't improperly modify the HashMap while iterating.
                for (Iterator<MapData> it = clientTileSources.values().iterator(); it.hasNext(); ) {
                    MapData mapData = it.next();
                    it.remove();
                    mapData.remove();
                }
                nativeDispose(mapPointer);
                mapPointer = 0;
                clientTileSources.clear();
                markers.clear();
            }
        });
    }

    /**
     * Load a new scene file synchronously.
     * Use {@link #setSceneLoadListener(SceneLoadListener)} for notification when the new scene is
     * ready.
     *
     * @param path Location of the YAML scene file within the application assets
     * @return Scene ID An identifier for the scene being loaded, the same value will be passed to
     * {@link SceneLoadListener#onSceneReady(int sceneId, SceneError sceneError)} when loading is complete.
     */
    public int loadSceneFile(String path) {
        return loadSceneFile(path, null);
    }

    /**
     * Load a new scene file asynchronously.
     * Use {@link #setSceneLoadListener(SceneLoadListener)} for notification when the new scene is
     * ready.
     *
     * @param path Location of the YAML scene file within the application assets
     * @return Scene ID An identifier for the scene being loaded, the same value will be passed to
     * {@link SceneLoadListener#onSceneReady(int sceneId, SceneError sceneError)} when loading is complete.
     */
    public int loadSceneFileAsync(String path) {
        return loadSceneFileAsync(path, null);
    }

    /**
     * Load a new scene file synchronously.
     * If scene updates triggers an error, they won't be applied.
     * Use {@link #setSceneLoadListener(SceneLoadListener)} for notification when the new scene is
     * ready.
     *
     * @param path         Location of the YAML scene file within the application assets
     * @param sceneUpdates List of {@code SceneUpdate}
     * @return Scene ID An identifier for the scene being loaded, the same value will be passed to
     * {@link SceneLoadListener#onSceneReady(int sceneId, SceneError sceneError)} when loading is complete.
     */
    public int loadSceneFile(String path, List<SceneUpdate> sceneUpdates) {
        String[] updateStrings = bundleSceneUpdates(sceneUpdates);
        checkPointer(mapPointer);
        int sceneId = nativeLoadScene(mapPointer, path, updateStrings);
        return sceneId;
    }

    /**
     * Load a new scene file asynchronously.
     * If scene updates triggers an error, they won't be applied.
     * Use {@link #setSceneLoadListener(SceneLoadListener)} for notification when the new scene is
     * ready.
     *
     * @param path         Location of the YAML scene file within the application assets
     * @param sceneUpdates List of {@code SceneUpdate}
     * @return Scene ID An identifier for the scene being loaded, the same value will be passed to
     * {@link SceneLoadListener#onSceneReady(int sceneId, SceneError sceneError)} when loading is complete.
     */
    public int loadSceneFileAsync(String path, List<SceneUpdate> sceneUpdates) {
        String[] updateStrings = bundleSceneUpdates(sceneUpdates);
        checkPointer(mapPointer);
        int sceneId = nativeLoadSceneAsync(mapPointer, path, updateStrings);
        return sceneId;
    }

    /**
     * Load a new scene synchronously, provided an explicit yaml scene string to load
     * If scene updates triggers an error, they won't be applied.
     * Use {@link #setSceneLoadListener(SceneLoadListener)} for notification when the new scene is
     * ready.
     *
     * @param yaml         YAML scene String
     * @param resourceRoot base path to resolve relative URLs
     * @param sceneUpdates List of {@code SceneUpdate}
     * @return Scene ID An identifier for the scene being loaded, the same value will be passed to
     */
    public int loadSceneYaml(String yaml, String resourceRoot, List<SceneUpdate> sceneUpdates) {
        String[] updateStrings = bundleSceneUpdates(sceneUpdates);
        checkPointer(mapPointer);
        int sceneId = nativeLoadSceneYaml(mapPointer, yaml, resourceRoot, updateStrings);
        return sceneId;
    }

    /**
     * Load a new scene asynchronously, provided an explicit yaml scene string to load
     * If scene updates triggers an error, they won't be applied.
     * Use {@link #setSceneLoadListener(SceneLoadListener)} for notification when the new scene is
     * ready.
     *
     * @param yaml         YAML scene String
     * @param resourceRoot base path to resolve relative URLs
     * @param sceneUpdates List of {@code SceneUpdate}
     * @return Scene ID An identifier for the scene being loaded, the same value will be passed to
     */
    public int loadSceneYamlAsync(String yaml, String resourceRoot, List<SceneUpdate> sceneUpdates) {
        String[] updateStrings = bundleSceneUpdates(sceneUpdates);
        checkPointer(mapPointer);
        int sceneId = nativeLoadSceneYamlAsync(mapPointer, yaml, resourceRoot, updateStrings);
        return sceneId;
    }

    /**
     * Apply SceneUpdates to the current scene asyncronously
     * If a updates trigger an error, scene updates won't be applied.
     * Use {@link #setSceneLoadListener(SceneLoadListener)} for notification when the new scene is
     * ready.
     *
     * @param sceneUpdates List of {@code SceneUpdate}
     * @return new scene ID
     */
    public int updateSceneAsync(List<SceneUpdate> sceneUpdates) {
        checkPointer(mapPointer);

        if (sceneUpdates == null || sceneUpdates.size() == 0) {
            throw new IllegalArgumentException("sceneUpdates can not be null or empty in queueSceneUpdates");
        }
        String[] updateStrings = bundleSceneUpdates(sceneUpdates);
        return nativeUpdateScene(mapPointer, updateStrings);
    }

    /**
     * Set the {@link HttpHandler} for retrieving remote map resources; a default-constructed
     * HttpHandler is suitable for most cases, but methods can be extended to modify resource URLs
     *
     * @param handler the HttpHandler to use
     */
    public void setHttpHandler(HttpHandler handler) {
        this.httpHandler = handler;
    }

    /**
     * Enables or disables the 3d buildings.
     *
     * @param enable
     */
    public void enable3dBuildings(Boolean enable) {
        SceneUpdate sceneUpdate = new SceneUpdate("global.show_3d_buildings", enable + "");
        List updates = new ArrayList();
        updates.add(sceneUpdate);
        updateSceneAsync(updates);
    }

    /**
     * Enables or disables the 3d buildings.
     *
     * @param enable
     */
    public void enableTransitLayer(Boolean enable) {
        SceneUpdate sceneUpdate = new SceneUpdate("global.transit_layer", enable + "");
        List updates = new ArrayList();
        updates.add(sceneUpdate);
        updateSceneAsync(updates);
    }

    /**
     * Set the geographic position of the center of the map view
     *
     * @param position LngLat of the position to set
     */
    public void setPosition(LatLng position) {
        if (position != null) {
            checkPointer(mapPointer);
            lastCenter = position;
            nativeSetPosition(mapPointer, position.getLng(), position.getLat());
        }
    }

    /**
     * Set the geographic position of the center of the map view with default easing
     *
     * @param position LngLat of the position to set
     * @param duration Time in milliseconds to ease to the given position
     */
    public void setPositionEased(LatLng position, int duration) {
        setPositionEased(position, duration, DEFAULT_EASE_TYPE, true);
    }

    /**
     * Set the geographic position of the center of the map view with custom easing
     *
     * @param position LngLat of the position to set
     * @param duration Time in milliseconds to ease to the given position
     * @param ease     Type of easing to use
     */
    public void setPositionEased(LatLng position, int duration, EaseType ease, boolean save) {
        float seconds = duration / 1000.f;
        checkPointer(mapPointer);
        if (save) {
            lastCenter = position;
        }
        nativeSetPositionEased(mapPointer, position.getLng(), position.getLat(), seconds, ease.ordinal());
    }

    public void setLatLngBounds(final LatLngBounds latlngBounds,
                                final float padding,
                                final long duration,
                                final Pair<Float, Float> vanishingPointOffset) {
        mapView.post(new Runnable() {
            @Override
            public void run() {
                kotlin.Pair<LatLng, Float> pair = latlngBounds.getVisibleBounds(
                        mapView.getWidth(),
                        mapView.getHeight(),
                        padding, vanishingPointOffset);

                if (duration <= 0) {
                    setZoom(pair.component2());
                    setPosition(pair.component1());
                } else {
                    setZoomEased(pair.component2(), (int) duration);
                    setPositionEased(pair.component1(), (int) duration);
                }
            }
        });
    }

    /**
     * Get the geographic position of the center of the map view
     *
     * @return LngLat of the center of the map view
     */
    LatLng getPosition() {
        double[] tmp = {0, 0};
        checkPointer(mapPointer);
        nativeGetPosition(mapPointer, tmp);
        return new LatLng(tmp[1], tmp[0]);
    }

    /**
     * Set the zoom level of the map view
     *
     * @param zoom Zoom level; lower values show more area
     */
    void setZoom(float zoom) {
        checkPointer(mapPointer);
        nativeSetZoom(mapPointer, zoom);
    }

    /**
     * Set the zoom level of the map view with default easing
     *
     * @param zoom     Zoom level; lower values show more area
     * @param duration Time in milliseconds to ease to given zoom
     */
    void setZoomEased(float zoom, int duration) {
        setZoomEased(zoom, duration, DEFAULT_EASE_TYPE);
    }

    /**
     * Set the zoom level of the map view with custom easing
     *
     * @param zoom     Zoom level; lower values show more area
     * @param duration Time in milliseconds to ease to given zoom
     * @param ease     Type of easing to use
     */
    void setZoomEased(float zoom, int duration, EaseType ease) {
        float seconds = duration / 1000.f;
        checkPointer(mapPointer);
        nativeSetZoomEased(mapPointer, zoom, seconds, ease.ordinal());
    }

    /**
     * Get the zoom level of the map view
     *
     * @return Zoom level; lower values show more area
     */
    public float getZoom() {
        checkPointer(mapPointer);
        return nativeGetZoom(mapPointer);
    }

    /**
     * Set the rotation of the view
     *
     * @param rotation Counter-clockwise rotation in radians; 0 corresponds to North pointing up
     */
    void setRotation(float rotation) {
        checkPointer(mapPointer);
        nativeSetRotation(mapPointer, rotation);
    }

    /**
     * Set the rotation of the view with default easing
     *
     * @param rotation Counter-clockwise rotation in radians; 0 corresponds to North pointing up
     * @param duration Time in milliseconds to ease to the given rotation
     */
    public void setRotationEased(float rotation, int duration) {
        setRotationEased(rotation, duration, DEFAULT_EASE_TYPE);
    }

    /**
     * Set the rotation of the view with custom easing
     *
     * @param rotation Counter-clockwise rotation in radians; 0 corresponds to North pointing up
     * @param duration Time in milliseconds to ease to the given rotation
     * @param ease     Type of easing to use
     */
    public void setRotationEased(float rotation, int duration, EaseType ease) {
        float seconds = duration / 1000.f;
        checkPointer(mapPointer);
        nativeSetRotationEased(mapPointer, rotation, seconds, ease.ordinal());
    }

    /**
     * Get the rotation of the view
     *
     * @return Counter-clockwise rotation in radians; 0 corresponds to North pointing up
     */
    public float getRotation() {
        checkPointer(mapPointer);
        return nativeGetRotation(mapPointer);
    }

    /**
     * Set the tilt angle of the view
     *
     * @param tilt Tilt angle in radians; 0 corresponds to straight down
     */
    public void setTilt(float tilt) {
        checkPointer(mapPointer);
        nativeSetTilt(mapPointer, tilt);
    }

    /**
     * Set the tilt angle of the view with default easing
     *
     * @param tilt     Tilt angle in radians; 0 corresponds to straight down
     * @param duration Time in milliseconds to ease to the given tilt
     */
    public void setTiltEased(float tilt, int duration) {
        setTiltEased(tilt, duration, DEFAULT_EASE_TYPE);
    }

    /**
     * Set the tilt angle of the view with custom easing
     *
     * @param tilt     Tilt angle in radians; 0 corresponds to straight down
     * @param duration Time in milliseconds to ease to the given tilt
     * @param ease     Type of easing to use
     */
    public void setTiltEased(float tilt, long duration, EaseType ease) {
        float seconds = duration / 1000.f;
        checkPointer(mapPointer);
        nativeSetTiltEased(mapPointer, tilt, seconds, ease.ordinal());
    }

    /**
     * Get the tilt angle of the view
     *
     * @return Tilt angle in radians; 0 corresponds to straight down
     */
    public float getTilt() {
        checkPointer(mapPointer);
        return nativeGetTilt(mapPointer);
    }

    /**
     * Set the camera type for the map view
     *
     * @param type A {@code CameraType}
     */
    public void setCameraType(CameraType type) {
        checkPointer(mapPointer);
        nativeSetCameraType(mapPointer, type.ordinal());
    }

    /**
     * Get the camera type currently in use for the map view
     *
     * @return A {@code CameraType}
     */
    public CameraType getCameraType() {
        checkPointer(mapPointer);
        return CameraType.values()[nativeGetCameraType(mapPointer)];
    }

    /**
     * Find the geographic coordinates corresponding to the given position on screen
     *
     * @param screenPosition Position in pixels from the top-left corner of the map area
     * @return LngLat corresponding to the given point, or null if the screen position
     * does not intersect a geographic location (this can happen at high tilt angles).
     */
    public LatLng screenPositionToLatLng(PointF screenPosition) {
        double[] tmp = {screenPosition.x, screenPosition.y};
        checkPointer(mapPointer);
        if (nativeScreenPositionToLngLat(mapPointer, tmp)) {
            return new LatLng(tmp[1], tmp[0]);
        }
        return null;
    }

    /**
     * Find the position on screen corresponding to the given geographic coordinates
     *
     * @param lngLat Geographic coordinates
     * @return Position in pixels from the top-left corner of the map area (the point
     * may not lie within the viewable screen area)
     */
    public PointF latLngToScreenPosition(LatLng lngLat) {
        double[] tmp = {lngLat.getLng(), lngLat.getLat()};
        checkPointer(mapPointer);
        nativeLngLatToScreenPosition(mapPointer, tmp);
        return new PointF((float) tmp[0], (float) tmp[1]);
    }

    /**
     * Construct a collection of drawable map features.
     *
     * @param name The name of the data collection. Once added to a map, features from this
     *             {@code MapData} will be available from a data source with this name, just like a data source
     *             specified in a scene file. You cannot create more than one data source with the same name.
     *             If you call {@code addDataLayer} with the same name more than once, the same {@code MapData}
     *             object will be returned.
     */
    private MapData addDataLayer(String name) {
        return addDataLayer(name, false);
    }

    /**
     * Construct a collection of drawable map features.
     *
     * @param name             The name of the data collection. Once added to a map, features from this
     * @param generateCentroid boolean to control <a href=
     *                         "https://mapzen.com/documentation/tangram/sources/#generate_label_centroids"> label centroid
     *                         generation</a> for polygon geometry
     *                         {@code MapData} will be available from a data source with this name, just like a data source
     *                         specified in a scene file. You cannot create more than one data source with the same name.
     *                         If you call {@code addDataLayer} with the same name more than once, the same {@code MapData}
     *                         object will be returned.
     */
    private MapData addDataLayer(String name, boolean generateCentroid) {
        checkPointer(mapPointer);
        long pointer = nativeAddTileSource(mapPointer, name, generateCentroid);
        if (pointer <= 0) {
            throw new RuntimeException("Unable to create new data source");
        }
        MapData mapData = new MapData(name, pointer, this);
        clientTileSources.put(name, mapData);
        return mapData;
    }

    /**
     * For package-internal use only; remove a {@code MapData} from this map
     *
     * @param mapData The {@code MapData} to remove
     */
    void removeDataLayer(MapData mapData) {
        clientTileSources.remove(mapData.getName());
        checkPointer(mapPointer);
        checkPointer(mapData.getId());
        nativeRemoveTileSource(mapPointer, mapData.getId());
    }

    /**
     * Manually trigger a re-draw of the map view
     * <p>
     * Typically this does not need to be called from outside Tangram, see {@link #setRenderMode(int)}.
     */
    @Keep
    public void requestRender() {
        mapView.requestRender();
    }

    /**
     * Set whether the map view re-draws continuously
     * <p>
     * Typically this does not need to be called from outside Tangram. The map automatically re-renders when the view
     * changes or when any animation in the map requires rendering.
     *
     * @param renderMode Either 1, to render continuously, or 0, to render only when needed.
     */
    @Keep
    public void setRenderMode(int renderMode) {
        mapView.setRenderMode(renderMode);
    }

    /**
     * Set a responder for tap gestures
     *
     * @param responder TapResponder to call
     */
    public void setTapResponder(final TouchInput.TapResponder responder) {
        touchInput.setTapResponder(new TouchInput.TapResponder() {
            @Override
            public boolean onSingleTapUp(float x, float y) {
                return responder != null && responder.onSingleTapUp(x, y);
            }

            @Override
            public boolean onSingleTapConfirmed(float x, float y) {
                return responder != null && responder.onSingleTapConfirmed(x, y);
            }
        });
    }

    /**
     * Set a responder for double-tap gestures
     *
     * @param responder DoubleTapResponder to call
     */
    public void setDoubleTapResponder(final TouchInput.DoubleTapResponder responder) {
        touchInput.setDoubleTapResponder(new TouchInput.DoubleTapResponder() {
            @Override
            public boolean onDoubleTap(float x, float y) {
                return responder != null && responder.onDoubleTap(x, y);
            }
        });
    }

    /**
     * Set a responder for long press gestures
     *
     * @param responder LongPressResponder to call
     */
    public void setLongPressResponder(final TouchInput.LongPressResponder responder) {
        touchInput.setLongPressResponder(new TouchInput.LongPressResponder() {
            @Override
            public void onLongPress(float x, float y) {
                if (responder != null) {
                    responder.onLongPress(x, y);
                }
            }
        });
    }

    /**
     * Set a responder for pan gestures
     *
     * @param responder PanResponder to call; if onPan returns true, normal panning behavior will not occur
     */
    public void setPanResponder(final TouchInput.PanResponder responder) {
        touchInput.setPanResponder(new TouchInput.PanResponder() {
            @Override
            public boolean onPan(float startX, float startY, float endX, float endY) {
                if (responder == null || !responder.onPan(startX, startY, endX, endY)) {
                    nativeHandlePanGesture(mapPointer, startX, startY, endX, endY);
                }
                return true;
            }

            @Override
            public boolean onFling(float posX, float posY, float velocityX, float velocityY) {
                if (responder == null || !responder.onFling(posX, posY, velocityX, velocityY)) {
                    nativeHandleFlingGesture(mapPointer, posX, posY, velocityX, velocityY);
                }
                return true;
            }
        });
    }

    /**
     * Set a responder for rotate gestures
     *
     * @param responder RotateResponder to call; if onRotate returns true, normal rotation behavior will not occur
     */
    public void setRotateResponder(final TouchInput.RotateResponder responder) {
        touchInput.setRotateResponder(new TouchInput.RotateResponder() {
            @Override
            public boolean onRotate(float x, float y, float rotation) {
                if (responder == null || !responder.onRotate(x, y, rotation)) {
                    nativeHandleRotateGesture(mapPointer, x, y, rotation);
                }
                return true;
            }
        });
    }

    /**
     * Set a responder for scale gestures
     *
     * @param responder ScaleResponder to call; if onScale returns true, normal scaling behavior will not occur
     */
    public void setScaleResponder(final TouchInput.ScaleResponder responder) {
        touchInput.setScaleResponder(new TouchInput.ScaleResponder() {
            @Override
            public boolean onScale(float x, float y, float scale, float velocity) {
                if (responder == null || !responder.onScale(x, y, scale, velocity)) {
                    nativeHandlePinchGesture(mapPointer, x, y, scale, velocity);
                }
                return true;
            }
        });
    }

    /**
     * Set a responder for shove (vertical two-finger drag) gestures
     *
     * @param responder ShoveResponder to call; if onShove returns true, normal tilting behavior will not occur
     */
    public void setShoveResponder(final TouchInput.ShoveResponder responder) {
        touchInput.setShoveResponder(new TouchInput.ShoveResponder() {
            @Override
            public boolean onShove(float distance) {
                if (responder == null || !responder.onShove(distance)) {
                    nativeHandleShoveGesture(mapPointer, distance);
                }
                return true;
            }
        });
    }

    /**
     * Set whether the gesture {@code second} can be recognized while {@code first} is in progress
     *
     * @param first   Initial gesture type
     * @param second  Subsequent gesture type
     * @param allowed True if {@code second} should be recognized, else false
     */
    public void setSimultaneousGestureAllowed(Gestures first, Gestures second, boolean allowed) {
        touchInput.setSimultaneousDetectionAllowed(first, second, allowed);
    }

    /**
     * Get whether the gesture {@code second} can be recognized while {@code first} is in progress
     *
     * @param first  Initial gesture type
     * @param second Subsequent gesture type
     * @return True if {@code second} will be recognized, else false
     */
    public boolean isSimultaneousGestureAllowed(Gestures first, Gestures second) {
        return touchInput.isSimultaneousDetectionAllowed(first, second);
    }

    /**
     * Set the radius to use when picking features on the map. The default radius is 0.5 dp.
     *
     * @param radius The radius in dp (density-independent pixels).
     */
    public void setPickRadius(float radius) {
        checkPointer(mapPointer);
        nativeSetPickRadius(mapPointer, radius);
    }


    /**
     * Set a listener for scene update error statuses
     *
     * @param listener The {@link SceneLoadListener} to call after scene has loaded
     */
    public void setSceneLoadListener(final SceneLoadListener listener) {
        sceneLoadListener = listener;
    }

    /**
     * Set a listener for label pick events
     *
     * @param listener The {@link LabelPickListener} to call
     */
    public void setLabelPickListener(final LabelPickListener listener) {
        labelPickListener = (listener == null) ? null : new LabelPickListener() {
            @Override
            public void onLabelPick(final LabelPickResult labelPickResult, final float positionX, final float positionY) {
                uiThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onLabelPick(labelPickResult, positionX, positionY);
                    }
                });
            }
        };
    }

    void setAnnotationClickListener(OnAnnotationClickListener listener) {
        annotationClickListener = listener;
        setMarkerPickListener();
        setFeaturePickListener();
    }

    /**
     * Set a listener for feature pick events
     */
    private void setFeaturePickListener() {
        featurePickListener = new FeaturePickListener() {
            @Override
            public void onFeaturePick(Map<String, String> properties, final float positionX, final float positionY) {
                if (properties.size() > 0) {
                    final Annotation annotation = pickAnnotation(properties);

                    uiThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (annotation != null) {
                                annotationClickListener.onAnnotationClicked(annotation);
                            }
                        }
                    });
                }
            }
        };
    }

    /**
     * Returns the annotation for the given properties that includes `id`.
     *
     * @param properties
     * @return clicked annotation
     */
    private Annotation pickAnnotation(Map<String, String> properties) {
        Annotation annotation = null;

        for (Map.Entry<String, String> stringStringEntry : properties.entrySet()) {
            if (stringStringEntry.getKey().equals("id")) {
                long id = Long.valueOf(stringStringEntry.getValue());

                if (polylines.containsKey(id)) {
                    annotation = polylines.get(id);
                    break;
                } else if (polygons.containsKey(id)) {
                    annotation = polygons.get(id);
                    break;
                }
            }
        }
        return annotation;
    }

    /**
     * Set a listener for marker pick events
     */
    private void setMarkerPickListener() {
        markerPickListener = new MarkerPickListener() {
            @Override
            public void onMarkerPick(final MarkerPickResult markerPickResult, final float positionX, final float positionY) {
                if (markerPickResult == null) {
                    return;
                }

                Marker pickedMarker = null;
                long id = markerPickResult.getMarker().getIdForMap(MapController.this);
                for (Marker marker : markers.values()) {
                    if (marker.getIdForMap(MapController.this) == id) {
                        pickedMarker = marker;
                        break;
                    }
                }

                final Marker finalPickedMarker = pickedMarker;
                uiThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (finalPickedMarker != null && annotationClickListener != null) {
                            annotationClickListener.onAnnotationClicked(finalPickedMarker);
                        }
                    }
                });
            }
        };
    }

    /**
     * Query the map for geometry features at the given screen coordinates; results will be returned
     * in a callback to the object set by {@link #setFeaturePickListener()}
     *
     * @param posX The horizontal screen coordinate
     * @param posY The vertical screen coordinate
     */
    public void pickFeature(float posX, float posY) {
        if (featurePickListener != null) {
            checkPointer(mapPointer);
            nativePickFeature(mapPointer, posX, posY, featurePickListener);
        }
    }

    /**
     * Query the map for labeled features at the given screen coordinates; results will be returned
     * in a callback to the object set by {@link #setLabelPickListener(LabelPickListener)}
     *
     * @param posX The horizontal screen coordinate
     * @param posY The vertical screen coordinate
     */
    public void pickLabel(float posX, float posY) {
        if (labelPickListener != null) {
            checkPointer(mapPointer);
            nativePickLabel(mapPointer, posX, posY, labelPickListener);
        }
    }

    /**
     * Query the map for a {@link Marker} at the given screen coordinates; results will be returned
     *
     * @param posX The horizontal screen coordinate
     * @param posY The vertical screen coordinate
     */
    void pickMarker(float posX, float posY) {
        if (markerPickListener != null) {
            checkPointer(mapPointer);
            nativePickMarker(this, mapPointer, posX, posY, markerPickListener);
        }
    }

    /**
     * Adds a {@link Marker} to the map which can be used to dynamically add rings and polylines
     * to the map.
     *
     * @return Newly created {@link Marker} object.
     */
    public Marker addMarker(MarkerOptions markerOptions) {
        checkPointer(mapPointer);
        long markerId = nativeMarkerAdd(mapPointer);
        Marker marker = new Marker(mapView.getContext(),
                markerOptions,
                markerId,
                this);

        markers.put(markerId, marker);
        return marker;
    }

    public Polyline addPolyline(PolylineOptions polylineOptions) {
        checkPointer(mapPointer);

        String layerName = polylineOptions.getLayerName();

        if (TextUtils.isEmpty(layerName)) {
            layerName = POLYLINE_LAYER_NAME;
        }

        MapData polylineData = addDataLayer(layerName);

        Polyline polyline = new Polyline(
                mapView.getContext(),
                polylineData.getId(),
                polylineOptions,
                this
        );

        polylineData.addPolyline(polyline);
        mapDatas.put(polylineData.getId(), polylineData);
        polylines.put(polylineData.getId(), polyline);

        requestRender();
        return polyline;
    }

    public Polygon addPolygon(PolygonOptions polygonOptions) {
        checkPointer(mapPointer);

        String layerName = polygonOptions.getLayerName();

        if (TextUtils.isEmpty(layerName)) {
            layerName = POLYGON_LAYER_NAME;
        }

        MapData polygonLayer = addDataLayer(layerName);

        Polygon polygon = new Polygon(
                mapView.getContext(),
                polygonLayer.getId(),
                polygonOptions,
                this
        );

        polygonLayer.addPolygon(polygon);
        mapDatas.put(polygonLayer.getId(), polygonLayer);
        polygons.put(polygonLayer.getId(), polygon);

        requestRender();
        return polygon;
    }

    public long addAnnotation(Annotation annotation) {
        checkPointer(mapPointer);

        if (annotation instanceof Marker) {
            long markerId = nativeMarkerAdd(mapPointer);
            markers.put(markerId, (Marker) annotation);
            return markerId;

        } else if (annotation instanceof Polyline) {
            String layerName = ((Polyline) annotation).getLayerName();

            if (TextUtils.isEmpty(layerName)) {
                layerName = POLYLINE_LAYER_NAME;
            }

            MapData lineLayer = addDataLayer(layerName);
            lineLayer.addPolyline((Polyline) annotation);
            polylines.put(lineLayer.getId(), (Polyline) annotation);
            return lineLayer.getId();

        } else if (annotation instanceof Polygon) {
            String layerName = ((Polygon) annotation).getLayerName();

            if (TextUtils.isEmpty(layerName)) {
                layerName = POLYGON_LAYER_NAME;
            }

            MapData polygonLayer = addDataLayer(layerName);
            polygonLayer.addPolygon((Polygon) annotation);
            polygons.put(polygonLayer.getId(), (Polygon) annotation);
            return polygonLayer.getId();

        } else {
            return 0;
        }
    }

    /**
     * Re-adds the annotation to reflect style changes while reusing the [MapData].
     *
     * @param annotation
     */
    public void refreshAnnotation(Annotation annotation) {
        MapData mapData = mapDatas.get(annotation.getIdForMap(this));
        mapData.clear();

        if (annotation instanceof Polygon) {
            mapData.addPolygon((Polygon) annotation);

        } else if (annotation instanceof Polyline) {
            mapData.addPolyline((Polyline) annotation);
        }
    }

    /**
     * Removes the passed in {@link Marker} from the map.
     *
     * @param markerId of the marker
     * @return whether or not the marker was removed
     */
    public boolean removeMarker(long markerId) {
        checkPointer(mapPointer);
        checkId(markerId);
        markers.remove(markerId);
        return nativeMarkerRemove(mapPointer, markerId);
    }

    public void removePolyline(long polylineId) {
        checkPointer(mapPointer);
        checkId(polylineId);
        polylines.remove(polylineId);
        nativeRemoveTileSource(mapPointer, polylineId);
        requestRender();
    }

    public void removeAnnotation(Annotation annotation) {
        long annotationId = annotation.getIdForMap(this);

        if (annotation instanceof Polyline) {
            removePolyline(annotationId);
        } else if (annotation instanceof Polygon) {
            removePolygon(annotationId);
        }
    }

    private void hideTileSource(long polylineId) {
        nativeRemoveTileSource(mapPointer, polylineId);
    }

    private void showPolyline(long polylineId) {
        Polyline polyline = polylines.get(polylineId);

        String layerName = polyline.getLayerName();
        if (TextUtils.isEmpty(layerName)) {
            layerName = POLYLINE_LAYER_NAME;
        }

        MapData polylineData = addDataLayer(layerName);
        polylineData.addPolyline(polyline);
        polylines.remove(polylineId);
        polylines.put(polylineData.getId(), polyline);
    }

    private void showPolygon(long polygonId) {
        Polygon polygon = polygons.get(polygonId);

        String layerName = polygon.getLayerName();
        if (TextUtils.isEmpty(layerName)) {
            layerName = POLYGON_LAYER_NAME;
        }

        MapData polygonData = addDataLayer(layerName);
        polygonData.addPolygon(polygon);
        polygons.remove(polygonId);
        polygons.put(polygonData.getId(), polygon);
    }

    public void removePolygon(long polygonId) {
        checkPointer(mapPointer);
        checkId(polygonId);
        polygons.remove(polygonId);
        nativeRemoveTileSource(mapPointer, polygonId);
        requestRender();
    }

    /**
     * Remove all the {@link Marker} objects from the map.
     */
    private void removeAllMarkers() {
        checkPointer(mapPointer);
        nativeMarkerRemoveAll(mapPointer);
    }

    private void reAddMarkers() {
        if (markers.size() > 0) {
            HashMap<Long, Marker> tempMarkers = new HashMap<>();
            for (Marker marker : markers.values()) {
                long markerId = nativeMarkerAdd(mapPointer);
                marker.initAnnotation(this, markerId);
                tempMarkers.put(markerId, marker);
            }
            markers = tempMarkers;
        }
    }

    /**
     * Set a listener for view complete events.
     *
     * @param listener The {@link ViewCompleteListener} to call when the view is complete
     */
    public void setViewCompleteListener(final ViewCompleteListener listener) {
        viewCompleteListener = (listener == null) ? null : new ViewCompleteListener() {
            @Override
            public void onViewComplete() {
                uiThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onViewComplete();
                    }
                });
            }
        };
    }

    /**
     * Enqueue a Runnable to be executed synchronously on the rendering thread
     *
     * @param r Runnable to run
     */
    public void queueEvent(Runnable r) {
        mapView.queueEvent(r);
    }

    /**
     * Make a debugging feature active or inactive
     *
     * @param flag The feature to set
     * @param on   True to activate the feature, false to deactivate
     */
    public void setDebugFlag(DebugFlag flag, boolean on) {
        nativeSetDebugFlag(flag.ordinal(), on);
    }

    /**
     * Set whether the OpenGL state will be cached between subsequent frames. This improves
     * rendering efficiency, but can cause errors if your application code makes OpenGL calls.
     *
     * @param use Whether to use a cached OpenGL state; false by default
     */
    public void useCachedGlState(boolean use) {
        checkPointer(mapPointer);
        nativeUseCachedGlState(mapPointer, use);
    }

    /**
     * Sets an opaque background color used as default color when a scene is being loaded
     *
     * @param red   red component of the background color
     * @param green green component of the background color
     * @param blue  blue component of the background color
     */
    public void setDefaultBackgroundColor(float red, float green, float blue) {
        checkPointer(mapPointer);
        nativeSetDefaultBackgroundColor(mapPointer, red, green, blue);
    }

    public void reCenter() {
        if (lastCenter != null)
            setPositionEased(lastCenter, 200);
    }

    // Package private methods
    // =======================

    void onLowMemory() {
        checkPointer(mapPointer);
        nativeOnLowMemory(mapPointer);
    }

    void removeTileSource(long sourcePtr) {
        checkPointer(mapPointer);
        checkPointer(sourcePtr);
        nativeRemoveTileSource(mapPointer, sourcePtr);
    }

    void clearTileSource(long sourcePtr) {
        checkPointer(mapPointer);
        checkPointer(sourcePtr);
        nativeClearTileSource(mapPointer, sourcePtr);
    }

    void addFeature(long sourcePtr, double[] coordinates, int[] rings, String[] properties) {
        checkPointer(mapPointer);
        checkPointer(sourcePtr);
        nativeAddFeature(mapPointer, sourcePtr, coordinates, rings, properties);
    }

    void addGeoJson(long sourcePtr, String geoJson) {
        checkPointer(mapPointer);
        checkPointer(sourcePtr);
        nativeAddGeoJson(mapPointer, sourcePtr, geoJson);
    }

    void checkPointer(long ptr) {
        if (ptr <= 0) {
            throw new RuntimeException("Tried to perform an operation on an invalid id! This means you may have used an object that has been disposed and is no longer valid.");
        }
    }

    void checkId(long id) {
        if (id <= 0) {
            throw new RuntimeException("Tried to perform an operation on an invalid id! This means you may have used an object that has been disposed and is no longer valid.");
        }
    }

    private String[] bundleSceneUpdates(List<SceneUpdate> sceneUpdates) {

        String[] updateStrings = null;

        if (sceneUpdates != null) {
            updateStrings = new String[sceneUpdates.size() * 2];
            int index = 0;
            for (SceneUpdate sceneUpdate : sceneUpdates) {
                updateStrings[index++] = sceneUpdate.getPath();
                updateStrings[index++] = sceneUpdate.getValue();
            }
        }

        return updateStrings;
    }

    public boolean setMarkerStylingFromString(long markerId, String styleString) {
        checkPointer(mapPointer);
        checkId(markerId);
        return nativeMarkerSetStylingFromString(mapPointer, markerId, styleString);
    }

    boolean setMarkerStylingFromPath(long markerId, String path) {
        checkPointer(mapPointer);
        checkId(markerId);
        return nativeMarkerSetStylingFromPath(mapPointer, markerId, path);
    }

    public boolean setMarkerBitmap(long markerId, int width, int height, int[] data) {
        checkPointer(mapPointer);
        checkId(markerId);
        return nativeMarkerSetBitmap(mapPointer, markerId, width, height, data);
    }

    public boolean setMarkerPoint(long markerId, double lng, double lat) {
        checkPointer(mapPointer);
        checkId(markerId);
        return nativeMarkerSetPoint(mapPointer, markerId, lng, lat);
    }

    public boolean setMarkerPointEased(long markerId, double lng, double lat, int duration, EaseType ease) {
        checkPointer(mapPointer);
        checkId(markerId);
        float seconds = duration / 1000.f;
        return nativeMarkerSetPointEased(mapPointer, markerId, lng, lat, seconds, ease.ordinal());
    }

    public boolean setMarkerPolyline(long markerId, double[] coordinates, int count) {
        checkPointer(mapPointer);
        checkId(markerId);
        return nativeMarkerSetPolyline(mapPointer, markerId, coordinates, count);
    }

    public boolean setMarkerPolygon(long markerId, double[] coordinates, int[] rings, int count) {
        checkPointer(mapPointer);
        checkId(markerId);
        return nativeMarkerSetPolygon(mapPointer, markerId, coordinates, rings, count);
    }

    public void changeAnnotationVisibility(long id, boolean visibile) {
        checkPointer(mapPointer);
        checkId(id);

        if (markers.containsKey(id)) {
            setMarkerVisible(id, visibile);

        } else if (polylines.containsKey(id)) {
            if (!visibile) {
                hideTileSource(id);
            } else {
                showPolyline(id);
            }
        } else if (polygons.containsKey(id)) {
            if (!visibile) {
                hideTileSource(id);
            } else {
                showPolygon(id);
            }
        }
    }

    public boolean setMarkerVisible(long markerId, boolean visible) {
        checkPointer(mapPointer);
        checkId(markerId);
        return nativeMarkerSetVisible(mapPointer, markerId, visible);
    }

    public boolean setMarkerDrawOrder(long markerId, int drawOrder) {
        checkPointer(mapPointer);
        checkId(markerId);
        Boolean isSuccessful = nativeMarkerSetDrawOrder(mapPointer, markerId, drawOrder);
        if (isSuccessful) {
            requestRender();
        }

        return isSuccessful;
    }

    @Keep
    Marker markerById(long markerId) {
        return markers.get(markerId);
    }

    // Native methods
    // ==============

    static {
        System.loadLibrary("c++_shared");
        System.loadLibrary("tangram");
    }

    synchronized native void nativeOnLowMemory(long mapPtr);

    synchronized native long nativeInit(MapController instance, AssetManager assetManager);

    private synchronized native void nativeDispose(long mapPtr);

    private synchronized native int nativeLoadScene(long mapPtr, String path, String[] updateStrings);

    private synchronized native int nativeLoadSceneAsync(long mapPtr, String path, String[] updateStrings);

    private synchronized native int nativeLoadSceneYaml(long mapPtr, String yaml, String resourceRoot, String[] updateStrings);

    private synchronized native int nativeLoadSceneYamlAsync(long mapPtr, String yaml, String resourceRoot, String[] updateStrings);

    private synchronized native void nativeSetupGL(long mapPtr);

    private synchronized native void nativeResize(long mapPtr, int width, int height);

    private synchronized native boolean nativeUpdate(long mapPtr, float dt);

    private synchronized native void nativeRender(long mapPtr);

    private synchronized native void nativeSetPosition(long mapPtr, double lon, double lat);

    private synchronized native void nativeSetPositionEased(long mapPtr, double lon, double lat, float seconds, int ease);

    private synchronized native void nativeGetPosition(long mapPtr, double[] lonLatOut);

    private synchronized native void nativeSetZoom(long mapPtr, float zoom);

    private synchronized native void nativeSetZoomEased(long mapPtr, float zoom, float seconds, int ease);

    private synchronized native float nativeGetZoom(long mapPtr);

    private synchronized native void nativeSetRotation(long mapPtr, float radians);

    private synchronized native void nativeSetRotationEased(long mapPtr, float radians, float seconds, int ease);

    private synchronized native float nativeGetRotation(long mapPtr);

    private synchronized native void nativeSetTilt(long mapPtr, float radians);

    private synchronized native void nativeSetTiltEased(long mapPtr, float radians, float seconds, int ease);

    private synchronized native float nativeGetTilt(long mapPtr);

    private synchronized native boolean nativeScreenPositionToLngLat(long mapPtr, double[] coordinates);

    private synchronized native boolean nativeLngLatToScreenPosition(long mapPtr, double[] coordinates);

    private synchronized native void nativeSetPixelScale(long mapPtr, float scale);

    private synchronized native void nativeSetCameraType(long mapPtr, int type);

    private synchronized native int nativeGetCameraType(long mapPtr);

    private synchronized native void nativeHandleTapGesture(long mapPtr, float posX, float posY);

    private synchronized native void nativeHandleDoubleTapGesture(long mapPtr, float posX, float posY);

    private synchronized native void nativeHandlePanGesture(long mapPtr, float startX, float startY, float endX, float endY);

    private synchronized native void nativeHandleFlingGesture(long mapPtr, float posX, float posY, float velocityX, float velocityY);

    private synchronized native void nativeHandlePinchGesture(long mapPtr, float posX, float posY, float scale, float velocity);

    private synchronized native void nativeHandleRotateGesture(long mapPtr, float posX, float posY, float rotation);

    private synchronized native void nativeHandleShoveGesture(long mapPtr, float distance);

    private synchronized native int nativeUpdateScene(long mapPtr, String[] updateStrings);

    private synchronized native void nativeSetPickRadius(long mapPtr, float radius);

    private synchronized native void nativePickFeature(long mapPtr, float posX, float posY, FeaturePickListener listener);

    private synchronized native void nativePickLabel(long mapPtr, float posX, float posY, LabelPickListener listener);

    private synchronized native void nativePickMarker(MapController instance, long mapPtr, float posX, float posY, MarkerPickListener listener);

    private synchronized native long nativeMarkerAdd(long mapPtr);

    private synchronized native boolean nativeMarkerRemove(long mapPtr, long markerID);

    private synchronized native boolean nativeMarkerSetStylingFromString(long mapPtr, long markerID, String styling);

    private synchronized native boolean nativeMarkerSetStylingFromPath(long mapPtr, long markerID, String path);

    private synchronized native boolean nativeMarkerSetBitmap(long mapPtr, long markerID, int width, int height, int[] data);

    private synchronized native boolean nativeMarkerSetPoint(long mapPtr, long markerID, double lng, double lat);

    private synchronized native boolean nativeMarkerSetPointEased(long mapPtr, long markerID, double lng, double lat, float duration, int ease);

    private synchronized native boolean nativeMarkerSetPolyline(long mapPtr, long markerID, double[] coordinates, int count);

    private synchronized native boolean nativeMarkerSetPolygon(long mapPtr, long markerID, double[] coordinates, int[] rings, int count);

    private synchronized native boolean nativeMarkerSetVisible(long mapPtr, long markerID, boolean visible);

    private synchronized native boolean nativeMarkerSetDrawOrder(long mapPtr, long markerID, int drawOrder);

    private synchronized native void nativeMarkerRemoveAll(long mapPtr);

    private synchronized native void nativeUseCachedGlState(long mapPtr, boolean use);

    private synchronized native void nativeCaptureSnapshot(long mapPtr, int[] buffer);

    private synchronized native void nativeSetDefaultBackgroundColor(long mapPtr, float r, float g, float b);

    private native void nativeOnUrlComplete(long mapPtr, long requestHandle, byte[] rawDataBytes, String errorMessage);

    synchronized native long nativeAddTileSource(long mapPtr, String name, boolean generateCentroid);

    synchronized native void nativeRemoveTileSource(long mapPtr, long sourcePtr);

    synchronized native void nativeClearTileSource(long mapPtr, long sourcePtr);

    synchronized native void nativeAddFeature(long mapPtr, long sourcePtr, double[] coordinates, int[] rings, String[] properties);

    synchronized native void nativeAddGeoJson(long mapPtr, long sourcePtr, String geoJson);

    native void nativeSetDebugFlag(int flag, boolean on);

    // Private members
    // ===============

    private long mapPointer;
    private long time = System.nanoTime();
    private GLSurfaceView mapView;
    private AssetManager assetManager;
    private FontFileParser fontFileParser;
    private DisplayMetrics displayMetrics = new DisplayMetrics();
    private HttpHandler httpHandler;
    private FeaturePickListener featurePickListener;
    private SceneLoadListener sceneLoadListener;
    private LabelPickListener labelPickListener;
    private MarkerPickListener markerPickListener;
    private ViewCompleteListener viewCompleteListener;
    private FrameCaptureCallback frameCaptureCallback;
    private boolean frameCaptureAwaitCompleteView;
    private Map<String, MapData> clientTileSources = new HashMap<>();
    private Map<Long, Marker> markers = new HashMap<>();
    private Map<Long, Polyline> polylines = new HashMap<>();
    private Map<Long, Polygon> polygons = new HashMap<>();
    private Map<Long, MapData> mapDatas = new HashMap<>();
    private OnAnnotationClickListener annotationClickListener;

    private Handler uiThreadHandler;
    TouchInput touchInput;
    private LatLng lastCenter = null;

    // GLSurfaceView.Renderer methods
    // ==============================

    @Override
    public void onDrawFrame(GL10 gl) {
        long newTime = System.nanoTime();
        float delta = (newTime - time) / 1000000000.0f;
        time = newTime;

        if (mapPointer <= 0) {
            // No native instance is initialized, so stop here. This can happen during Activity
            // shutdown when the map has been disposed but the View hasn't been destroyed yet.
            return;
        }

        boolean viewComplete;
        synchronized (this) {
            viewComplete = nativeUpdate(mapPointer, delta);
            nativeRender(mapPointer);
        }

        if (viewComplete) {

            if (viewCompleteListener != null) {
                viewCompleteListener.onViewComplete();
            }
        }
        if (frameCaptureCallback != null) {
            if (!frameCaptureAwaitCompleteView || viewComplete) {
                frameCaptureCallback.onCaptured(capture());
                frameCaptureCallback = null;
            }
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {

        if (mapPointer <= 0) {
            // No native instance is initialized, so stop here. This can happen during Activity
            // shutdown when the map has been disposed but the View hasn't been destroyed yet.
            return;
        }

        nativeSetPixelScale(mapPointer, displayMetrics.density);
        nativeResize(mapPointer, width, height);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        if (mapPointer <= 0) {
            // No native instance is initialized, so stop here. This can happen during Activity
            // shutdown when the map has been disposed but the View hasn't been destroyed yet.
            return;
        }

        nativeSetupGL(mapPointer);
    }

    // Networking methods
    // ==================
    @Keep
    void cancelUrlRequest(long requestHandle) {
        if (httpHandler == null) {
            return;
        }
        httpHandler.onCancel(requestHandle);
    }

    @Keep
    void startUrlRequest(final String url, final long requestHandle) {
        if (httpHandler == null) {
            return;
        }

        Callback callback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                nativeOnUrlComplete(mapPointer, requestHandle, null, e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

                if (!response.isSuccessful()) {
                    nativeOnUrlComplete(mapPointer, requestHandle, null, response.message());
                    DebugUtils.logException(new IOException("Unexpected response code: " + response + " for URL: " + url));
                }
                byte[] bytes = response.body().bytes();
                nativeOnUrlComplete(mapPointer, requestHandle, bytes, null);
            }
        };

        httpHandler.onRequest(url, callback, requestHandle);
    }

    // Called from JNI on worker or render-thread.
    @Keep
    public void sceneReadyCallback(final int sceneId, final SceneError error) {
        final SceneLoadListener cb = sceneLoadListener;

        if (cb != null) {
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    // re-adding markers to the new scene.
                    reAddMarkers();

                    cb.onSceneReady(sceneId, error);
                }
            });
        }
    }

    // Font Fetching
    // =============
    @Keep
    String getFontFilePath(String key) {
        return fontFileParser.getFontFile(key);
    }

    @Keep
    String getFontFallbackFilePath(int importance, int weightHint) {
        return fontFileParser.getFontFallback(importance, weightHint);
    }


}
