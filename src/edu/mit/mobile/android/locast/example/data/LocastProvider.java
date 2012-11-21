package edu.mit.mobile.android.locast.example.data;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import edu.mit.mobile.android.content.ForeignKeyDBHelper;
import edu.mit.mobile.android.content.GenericDBHelper;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.content.m2m.M2MDBHelper;
import edu.mit.mobile.android.locast.data.JSONSyncableIdenticalChildFinder;
import edu.mit.mobile.android.locast.data.JsonSyncableItem;
import edu.mit.mobile.android.locast.data.NoPublicPath;
import edu.mit.mobile.android.locast.example.BuildConfig;
import edu.mit.mobile.android.locast.net.LocastApplicationCallbacks;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.sync.SyncableProvider;
import edu.mit.mobile.android.locast.sync.SyncableSimpleContentProvider;

public class LocastProvider extends SyncableSimpleContentProvider implements SyncableProvider {

    public static final String AUTHORITY = "edu.mit.mobile.android.locast.example";


    private static final int DB_VER = 1;

    private static final String TAG = LocastProvider.class.getSimpleName();

    private String mBaseUrl;

    public LocastProvider() {
        super(AUTHORITY, DB_VER);

        final GenericDBHelper casts = new GenericDBHelper(Cast.class);

        final ForeignKeyDBHelper castsMedia = new ForeignKeyDBHelper(Cast.class, CastMedia.class,
                CastMedia.CAST);

        final GenericDBHelper collections = new GenericDBHelper(Collection.class);

        final M2MDBHelper collectionCasts = new M2MDBHelper(collections, casts,
                new JSONSyncableIdenticalChildFinder());

        // /cast/
        // /cast/1/
        addDirAndItemUri(casts, Cast.PATH);

        // /cast/1/media/
        // /cast/1/media/1/
        addChildDirAndItemUri(castsMedia, Cast.PATH, CastMedia.PATH);

        // /collection/
        // /collection/1/
        addDirAndItemUri(collections, Collection.PATH);

        // /collection/1/cast/
        // /collection/1/cast/1/
        addChildDirAndItemUri(collectionCasts, Collection.PATH, Cast.PATH);

        // /collection/1/cast/1/media/
        // /collection/1/cast/1/media/1/
        addChildDirAndItemUri(castsMedia, Collection.PATH + "/#/" + Cast.PATH, CastMedia.PATH);

    }

    @Override
    public boolean canSync(Uri uri) {

        return true;
    }

    /**
     * Retrieves the parent of the given content: dir or item. This only handles content URIs from
     * this provider.
     *
     * @param content
     *            a dir or item content uri
     * @return the content: uri of the parent
     */
    public static Uri getParent(Uri content) {
        if (content.getPathSegments().size() == 1) {
            throw new IllegalArgumentException("Cannot retrieve parent for " + content);
        }
        return ProviderUtils.removeLastPathSegment(content);
    }

    /**
     * @param context
     * @param uri
     *            the URI of the item whose field should be queried
     * @param field
     *            the string name of the field
     * @return
     */
    private static String getPathFromField(Context context, Uri uri, String field) {
        String path = null;
        final String[] generalProjection = { JsonSyncableItem._ID, field };
        final Cursor c = context.getContentResolver().query(uri, generalProjection, null, null,
                null);
        try {
            if (c.getCount() == 1 && c.moveToFirst()) {
                final String storedPath = c.getString(c.getColumnIndex(field));
                path = storedPath;
            } else {
                throw new IllegalArgumentException("could not get path from field '" + field
                        + "' in uri " + uri);
            }
        } finally {
            c.close();
        }
        return path;
    }

    @Override
    public String getPostPath(Context context, Uri uri) throws NoPublicPath {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getPublicPath " + uri);
        }
        final String type = getType(uri);

        if (!type.startsWith(ProviderUtils.TYPE_ITEM_PREFIX)) {
            throw new IllegalArgumentException("getPostPath can only handle content items");
        }

        return getPublicPath(context, getParent(uri));
    }

    @Override
    public String getPublicPath(Context context, Uri uri) throws NoPublicPath {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getPublicPath " + uri);
        }
        final String type = getType(uri);

        if (mBaseUrl == null) {
            final NetworkClient nc = ((LocastApplicationCallbacks) context.getApplicationContext())
                    .getNetworkClient(context, null);
            mBaseUrl = nc.getBaseUrl();
        }

        // TODO this is the only hard-coded URL. This should be removed eventually.
        if (Cast.TYPE_DIR.equals(type)) {
            if (uri.getPathSegments().size() == 1) {
                return mBaseUrl + "cast/";
            } else {
                return getPathFromField(context, getParent(uri),
                        Collection.COL_CASTS_URI);
            }

        } else if (CastMedia.TYPE_DIR.equals(type)) {
            return getPathFromField(context, CastMedia.getParent(uri), Cast.COL_MEDIA_PUBLIC_URL);

            // TODO this too
        } else if (Collection.TYPE_DIR.equals(type)) {
            return mBaseUrl + "collection/";

            // all items are resolved this way, as the superclass can handle such.
        } else {
            return super.getPublicPath(context, uri);
        }
    }

    @Override
    public String getAuthority() {
        return AUTHORITY;
    }
}
