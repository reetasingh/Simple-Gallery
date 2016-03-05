package com.simplemobiletools.gallery.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.simplemobiletools.gallery.Constants;
import com.simplemobiletools.gallery.Helpers;
import com.simplemobiletools.gallery.MyViewPager;
import com.simplemobiletools.gallery.R;
import com.simplemobiletools.gallery.adapters.MyPagerAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ViewPagerActivity extends AppCompatActivity
        implements ViewPager.OnPageChangeListener, View.OnSystemUiVisibilityChangeListener, MediaScannerConnection.OnScanCompletedListener {
    private int pos;
    private boolean isFullScreen;
    private ActionBar actionbar;
    private List<String> photos;
    private MyViewPager pager;
    private String path;
    private String directory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);

        pos = 0;
        isFullScreen = true;
        actionbar = getSupportActionBar();
        hideSystemUI();

        path = getIntent().getStringExtra(Constants.PHOTO);
        directory = new File(path).getParent();
        pager = (MyViewPager) findViewById(R.id.view_pager);
        photos = getPhotos();
        if (isDirEmpty())
            return;

        final MyPagerAdapter adapter = new MyPagerAdapter(getSupportFragmentManager());
        adapter.setPaths(photos);
        pager.setAdapter(adapter);
        pager.setCurrentItem(pos);
        pager.addOnPageChangeListener(this);

        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(this);
        updateActionbarTitle();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.viewpager_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_share:
                shareImage();
                return true;
            case R.id.menu_remove:
                deleteImage();
                return true;
            case R.id.menu_edit:
                editImage();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void shareImage() {
        final String shareTitle = getResources().getString(R.string.share_via);
        final Intent sendIntent = new Intent();
        final File file = getCurrentFile();
        final Uri uri = Uri.fromFile(file);
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
        sendIntent.setType("image/*");
        startActivity(Intent.createChooser(sendIntent, shareTitle));
    }

    private void deleteImage() {
        Helpers.showToast(this, R.string.deleting);
        final File file = getCurrentFile();
        file.delete();
        MediaScannerConnection.scanFile(this, new String[]{path}, null, this);
    }

    private boolean isDirEmpty() {
        if (photos.size() <= 0) {
            deleteDirectoryIfEmpty();
            finish();
            return true;
        }
        return false;
    }

    private void editImage() {
        final File file = getCurrentFile();
        final String fullName = file.getName();
        final int pos = fullName.lastIndexOf(".");
        if (pos <= 0)
            return;

        final String name = fullName.substring(0, pos);
        final String extension = fullName.substring(pos + 1, fullName.length());

        final View renameFileView = getLayoutInflater().inflate(R.layout.rename_file, null);
        final EditText fileNameET = (EditText) renameFileView.findViewById(R.id.file_name);
        fileNameET.setText(name);

        final EditText extensionET = (EditText) renameFileView.findViewById(R.id.extension);
        extensionET.setText(extension);

        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(getResources().getString(R.string.rename_file));
        alertDialog.setView(renameFileView);

        alertDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String fileName = fileNameET.getText().toString().trim();
                final String extension = extensionET.getText().toString().trim();
                final File newFile = new File(file.getParent(), fileName + "." + extension);

                if (!fileName.isEmpty() && !extension.isEmpty() && file.renameTo(newFile)) {
                    photos.set(pager.getCurrentItem(), newFile.getAbsolutePath());

                    final String[] changedFiles = {file.getAbsolutePath(), newFile.getAbsolutePath()};
                    MediaScannerConnection.scanFile(getApplicationContext(), changedFiles, null, null);
                    updateActionbarTitle();
                } else {
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.rename_error), Toast.LENGTH_SHORT).show();
                }
            }
        });

        alertDialog.setNegativeButton("Cancel", null);
        alertDialog.show();
    }

    private void reloadViewPager() {
        final MyPagerAdapter adapter = (MyPagerAdapter) pager.getAdapter();
        final int pos = pager.getCurrentItem();
        photos = getPhotos();
        if (isDirEmpty())
            return;

        pager.setAdapter(null);
        adapter.updateItems(photos);
        pager.setAdapter(adapter);

        final int newPos = Math.min(pos, adapter.getCount());
        pager.setCurrentItem(newPos);
        updateActionbarTitle();
    }

    private void deleteDirectoryIfEmpty() {
        final File file = new File(directory);
        if (file.isDirectory() && file.listFiles().length == 0) {
            file.delete();
        }
    }

    private List<String> getPhotos() {
        final List<String> photos = new ArrayList<>();
        final Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        final String where = MediaStore.Images.Media.DATA + " like ? ";
        final String[] args = new String[]{directory + "%"};
        final String[] columns = {MediaStore.Images.Media.DATA};
        final String order = MediaStore.Images.Media.DATE_MODIFIED + " DESC";
        final Cursor cursor = getContentResolver().query(uri, columns, where, args, order);
        final String pattern = Pattern.quote(directory) + "/[^/]*";

        int i = 0;
        if (cursor != null && cursor.moveToFirst()) {
            final int pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
            do {
                final String curPath = cursor.getString(pathIndex);

                if (curPath.matches(pattern)) {
                    photos.add(curPath);

                    if (curPath.equals(path))
                        pos = i;

                    i++;
                }
            } while (cursor.moveToNext());
            cursor.close();
        }
        return photos;
    }

    public void photoClicked() {
        isFullScreen = !isFullScreen;
        if (isFullScreen) {
            hideSystemUI();
        } else {
            showSystemUI();
        }
    }

    private void hideSystemUI() {
        if (actionbar != null)
            actionbar.hide();

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LOW_PROFILE |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    private void showSystemUI() {
        if (actionbar != null)
            actionbar.show();

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void updateActionbarTitle() {
        setTitle(Helpers.getFilename(photos.get(pager.getCurrentItem())));
    }

    private File getCurrentFile() {
        return new File(photos.get(pager.getCurrentItem()));
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        updateActionbarTitle();
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
            isFullScreen = false;
        }
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                reloadViewPager();
            }
        });
    }
}