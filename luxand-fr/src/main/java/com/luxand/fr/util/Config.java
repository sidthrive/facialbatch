package com.luxand.fr.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static android.content.ContentValues.TAG;

/**
 * Created by wildan on 10/25/17.
 */

public class Config {

    private String serialKey;

    public String getSerialKey(String key, Context context) throws IOException {

        Properties prop = new Properties();
        AssetManager assetManager = context.getAssets();
        InputStream inputStream = assetManager.open("config.properties");
        prop.load(inputStream);
        return prop.getProperty(key);
    }

}
