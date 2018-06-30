package com.ts.app3.utils;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.ts.app3.R;


/**
 * Created by 77167 on 2018/6/9.
 * 请求网络是用到的提示框
 */

public class FnProgressDialog extends ProgressDialog {

    private static ProgressDialog progressDialog;

    public FnProgressDialog(Context context) {
        super(context);
    }

    public FnProgressDialog(Context context, int theme) {
        super(context, theme);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        // 得到加载view
        View v = inflater.inflate(R.layout.fn_progressdialog, null);
        // 加载布局
        LinearLayout layout = (LinearLayout) v.findViewById(R.id.ll_dialog_view);

        // ImageView控件
        ImageView dialogImage = (ImageView) v.findViewById(R.id.dialog_img);
        // 加载动画
        Animation animation = AnimationUtils.loadAnimation(getContext(), R.anim.progress_dialog);
        // 使用ImageView显示动画
        dialogImage.startAnimation(animation);

        //为显示dialog的圆角，不加会出现四个黑棱角
        Window window = getWindow();
//        window.setBackgroundDrawable(new BitmapDrawable());
        window.setBackgroundDrawableResource(android.R.color.transparent);

        //setContentView(R.layout.myprogressdialog);
        setContentView(layout, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

    }

    /**
     * 提示语为默认值
     */
    public static void showDialog(Context context){
        progressDialog=new FnProgressDialog(context, R.style.dialogBoxTheme);
        progressDialog.setCancelable(false);// 不可以用“返回键”取消
        progressDialog.setCanceledOnTouchOutside(false);//不可点击进度框外部取消
        progressDialog.setMessage("加载中...");
        progressDialog.show();
    }

    public static void showDialog(Context context,String msg){
        progressDialog=new FnProgressDialog(context, R.style.dialogBoxTheme);
        progressDialog.setCancelable(false);// 不可以用“返回键”取消
        progressDialog.setCanceledOnTouchOutside(false);//不可点击进度框外部取消
        progressDialog.setMessage(msg);
        progressDialog.show();
    }

    public static void closeDialog(){
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }
}
