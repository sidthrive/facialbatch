package com.luxand.fr.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

/**
 * Created by wildan on 10/25/17.
 */

public class Dialog {

    public void showErrorAndClose(Context context, String error, int code) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(error + ": " + code)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                })
                .show();
    }

    public void showMessage(Context context, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                })
                .setCancelable(false) // cancel with button only
                .show();
    }

}
