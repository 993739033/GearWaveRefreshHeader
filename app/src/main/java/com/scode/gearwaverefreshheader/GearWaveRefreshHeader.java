package com.scode.gearwaverefreshheader;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Shader;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import com.scwang.smartrefresh.layout.api.RefreshHeader;
import com.scwang.smartrefresh.layout.api.RefreshKernel;
import com.scwang.smartrefresh.layout.api.RefreshLayout;
import com.scwang.smartrefresh.layout.constant.RefreshState;
import com.scwang.smartrefresh.layout.constant.SpinnerStyle;
import com.scwang.smartrefresh.layout.util.DensityUtil;

/**
 * Created by ZWX on 2019/4/19.
 * 自定义双波形加载动画
 * * y=A*sin(ωx+φ)+k
 * * <p>
 * * A—振幅越大， 波形在y轴上最大与最小值的差值越大
 * * ω—角速度 ， 控制正弦周期(单位角度内震动的次数)
 * * φ—初相   ， 反映在坐标系上则为图像的左右移动。这里通过不断改变φ,达到波浪移动效果
 * * k—偏距    ， 反映在坐标系上则为图像的上移或下移。
 */
public class GearWaveRefreshHeader extends View implements RefreshHeader {
    /**
     * 振幅
     */
    private float A;
    private float scaleA = 0.7F;//控制A的大小
    /**
     * 偏距
     */
    private int K;
    /**
     * 波形的颜色
     */
    //0xaaFF7E37
    private int waveColor = 0xcc51b806;
    private int backColor = 0xcccccccc;
    /**
     * 初相
     */
    private float φ;
    /**
     * 波形移动的速度
     */
    private float waveSpeed1 = 8F;
    private float waveSpeed2 = 5F;
    /**
     * 角速度
     */
    private double ω1;
    private double ω2;
    /**
     * 两曲线相差多少个周期
     */
    private double DPeriod = 0.3;
    /**
     * 记录View设置的高度
     */
    private int mViewHeight;

    private Path wavePath1;//第一个波形path
    private Path wavePath2;//第二个波形path

    private Paint wavePaint;//波浪画笔
    private Paint mBackPaint;//背景画笔

    private RefreshState mState;

    private ValueAnimator waveAnimator;
    private ValueAnimator gearAnimator;
    float R_DP = 13;
    float Base_R = dp2px(R_DP);//dp   绘制齿轮基准半径

    /**
     * 以下参数和齿轮有关
     */
    Paint insiderRingPaint;//内部环形绘制画笔
    Paint outsiderRingPaint;//外部环形绘制画笔
    Paint gearPaint;//齿轮绘制画笔
    int gearColor = 0xECFFD08C;//齿轮颜色
    int gearOutCount = 8;//齿轮外部的齿合个数
    float gearOutBottomRatio = 0.8f;//齿合的底部占已分大小的比例
    float gearOutTopRatio = 0.4f;//齿合的顶部占已分大小的比例
    float insideRingWidthRatio = 1F;//内环的宽度比例(以下比例都先对于Base_R)
    float outsideRingWidthRatio = 1F;//外环的宽度比例
    float gearWidthRatio = 1.2F;//外部齿合的高度比例
    PointF gearCenter;//齿轮中心
    float startAngle = 0;//齿轮的开始角度
    int animCircleTime = 1000;//动画一周期时长
    boolean isClockWise = false;//是否为顺时针

    float offsetTemp = 0;
    float lastOffsetTemp = 0;//存储上一次变化的长度

    boolean isGearAniming = false;//是否在进行gearAnim动画中


    public GearWaveRefreshHeader(Context context) {
        super(context);
        initView(context);
    }

    public GearWaveRefreshHeader(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public GearWaveRefreshHeader(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    private void initView(Context context) {
        setMinimumHeight(DensityUtil.dp2px(80));
        initWavePaint();
        initGearPaint();
        initAnimation();
    }

    private void initWavePaint() {
        wavePath1 = new Path();
        wavePath2 = new Path();
        wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint.setAntiAlias(true);
        wavePaint.setStyle(Paint.Style.FILL_AND_STROKE);
//        wavePaint.setColor(waveColor);

        mBackPaint = new Paint();
        mBackPaint.setColor(backColor);
        mBackPaint.setAntiAlias(true);
    }


    private void initGearPaint() {
        gearPaint = new Paint();
        gearPaint.setAntiAlias(true);
        gearPaint.setDither(true);
        gearPaint.setStyle(Paint.Style.FILL);
        gearPaint.setColor(gearColor);
        gearPaint.setStrokeJoin(Paint.Join.ROUND);

        insiderRingPaint = new Paint();
        insiderRingPaint.setAntiAlias(true);
        insiderRingPaint.setDither(true);
        insiderRingPaint.setStrokeWidth(insideRingWidthRatio * Base_R);
        insiderRingPaint.setStyle(Paint.Style.STROKE);
        insiderRingPaint.setColor(gearColor);

        outsiderRingPaint = new Paint();
        outsiderRingPaint.setAntiAlias(true);
        outsiderRingPaint.setDither(true);
        outsiderRingPaint.setStrokeWidth(outsideRingWidthRatio * Base_R);
        outsiderRingPaint.setStyle(Paint.Style.STROKE);
        outsiderRingPaint.setColor(gearColor);
    }

    private void initAnimation() {
        waveAnimator = ValueAnimator.ofInt(0, getWidth());
        waveAnimator.setDuration(1200);
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.setInterpolator(new LinearInterpolator());
        waveAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                /**
                 * 刷新页面调取onDraw方法，通过变更φ 达到移动效果
                 */
                invalidate();
            }
        });


        gearAnimator = ValueAnimator.ofFloat(0, isClockWise ? 360 : -360);
        gearAnimator.setDuration(animCircleTime);
        gearAnimator.setRepeatCount(ValueAnimator.INFINITE);//表示无限
        gearAnimator.setInterpolator(new LinearInterpolator());
        gearAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                startAngle = (float) animation.getAnimatedValue();
                postInvalidate();
            }
        });
        gearAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                isGearAniming = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                isGearAniming = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(backColor);
        drawWaveView(canvas);

    }

    private void drawWaveView(Canvas canvas) {
        fillBottom1(canvas);
        drawGearView(canvas);
        fillBottom2(canvas);
    }

    private void drawGearView(Canvas canvas) {
        if (offsetTemp > 0) {
            drawGear(canvas);
            drawInsideRing(canvas);
            drawOutsideRing(canvas);
        }
    }

    //画内环
    private void drawInsideRing(Canvas canvas) {
        canvas.drawCircle(gearCenter.x, gearCenter.y, 3 * Base_R / 2, insiderRingPaint);
    }

    //画外环
    private void drawOutsideRing(Canvas canvas) {
        canvas.drawCircle(gearCenter.x, gearCenter.y, 3 * Base_R, outsiderRingPaint);
    }

    //画外部齿合
    private void drawGear(Canvas canvas) {
        Path path = initGearPath();
        canvas.drawPath(path, gearPaint);
    }

    private Path initGearPath() {
        Path path = new Path();
        float insideR = (3 * Base_R + outsideRingWidthRatio * Base_R / 2);
        float outsideR = (insideR + Base_R * gearWidthRatio);
        float temp = (-outsideR + offsetTemp) * 1.2f;
        gearCenter.y = temp > 0 ? 0 : temp;//y不超过0点
        if (isClockWise) {
            startAngle += (offsetTemp - lastOffsetTemp) * 1.2f;
        } else {
            startAngle -= (offsetTemp - lastOffsetTemp) * 1.2f;
        }
        lastOffsetTemp = offsetTemp;

        Path insidePath = new Path();
        Path outPath = new Path();
        RectF insideRectF = new RectF(gearCenter.x - insideR, gearCenter.y - insideR, gearCenter.x + insideR, gearCenter.y + insideR);
        insidePath.addArc(insideRectF, startAngle, 359.9F);

        RectF outSideRectF = new RectF(gearCenter.x - outsideR, gearCenter.y - outsideR, gearCenter.x + outsideR, gearCenter.y + outsideR);
        outPath.addArc(outSideRectF, startAngle, 359.9F);

        PathMeasure insidePathMeasure = new PathMeasure(insidePath, true);
        PathMeasure outsidePathMeasure = new PathMeasure(outPath, true);
        float insideItemLength = insidePathMeasure.getLength() / gearOutCount;
        float outsideItemLength = outsidePathMeasure.getLength() / gearOutCount;
        float insideD = (1 - gearOutBottomRatio) / 2;//获取内部差值
        float outsideD = (1 - gearOutTopRatio) / 2;//获取外部差值

        for (int i = 0; i < gearOutCount; i++) {
            Path itemPath = new Path();
            float[] pos = new float[2];
            insidePathMeasure.getPosTan(i * insideItemLength + insideD * insideItemLength, pos, null);//获取点信息
            itemPath.moveTo(pos[0], pos[1]);
            outsidePathMeasure.getPosTan(i * outsideItemLength + outsideD * outsideItemLength, pos, null);//获取点信息
            itemPath.lineTo(pos[0], pos[1]);

            outsidePathMeasure.getPosTan((i + 1) * outsideItemLength - outsideD * outsideItemLength, pos, null);//获取点信息
            itemPath.lineTo(pos[0], pos[1]);

            insidePathMeasure.getPosTan((i + 1) * insideItemLength - insideD * insideItemLength, pos, null);//获取点信息
            itemPath.lineTo(pos[0], pos[1]);
            itemPath.close();
            path.addPath(itemPath);
        }
        return path;
    }

    /**
     * 填充波浪下面部分
     */
    private void fillBottom1(Canvas canvas) {

        if (waveAnimator.isRunning()) {
            φ -= waveSpeed1 / 100;

        }
        float y;
        wavePath1.reset();
        wavePath1.moveTo(0, mViewHeight / 2);

        if (A >= 0) {
            for (float x = 0; x <= getWidth(); x += 20) {
                y = (float) (A * Math.sin(ω1 * x + φ) + mViewHeight * 0.6F);
                wavePath1.lineTo(x, y);
            }
        } else {
            wavePath1.lineTo(getWidth(), mViewHeight / 2);
        }
        //填充矩形
        wavePath1.lineTo(getWidth(), getHeight());
        wavePath1.lineTo(0, getHeight());
        wavePath1.close();

        canvas.drawPath(wavePath1, wavePaint);
    }

    /**
     * 填充波浪下面部分
     */
    private void fillBottom2(Canvas canvas) {

        if (waveAnimator.isRunning()) {
            φ -= waveSpeed2 / 100;

        }
        float y;
        wavePath2.reset();
        wavePath2.moveTo(0, mViewHeight / 2);

        if (A >= 0) {
            for (float x = 0; x <= getWidth(); x += 20) {
                y = (float) (A * Math.sin(ω2 * x + φ + Math.PI * DPeriod) + mViewHeight * 0.6F);
                wavePath2.lineTo(x, y);
            }
        } else {
            wavePath2.lineTo(getWidth(), mViewHeight / 2);
        }
        //填充矩形
        wavePath2.lineTo(getWidth(), getHeight());
        wavePath2.lineTo(0, getHeight());
        wavePath2.close();

        canvas.drawPath(wavePath2, wavePaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        ω1 = 2 * Math.PI / getWidth();
        ω2 = ω1 * 1.3f;
        gearCenter = new PointF(getWidth() / 2, 0);
        LinearGradient gradient = new LinearGradient(0, mViewHeight / 2,
                0, 1.5F * h, waveColor, 0xCCC0FF8C, Shader.TileMode.CLAMP);
        wavePaint.setShader(gradient);
    }


    @NonNull
    @Override
    public View getView() {
        return this;
    }

    @NonNull
    @Override
    public SpinnerStyle getSpinnerStyle() {
        return SpinnerStyle.Scale;
    }

    @Override
    public void setPrimaryColors(int... colors) {
        if (colors.length > 0) {
            backColor = colors[0];
            if (colors.length > 1) {
                wavePaint.setColor(colors[1]);
            }
        }
    }

    @Override
    public void onInitialized(@NonNull RefreshKernel kernel, int height, int extendHeight) {

    }

    @Override
    public void onPulling(float percent, int offset, int height, int extendHeight) {
        mViewHeight = height;
//        LogUtil.d("offest:>>" + offset + "  height:>>" + height);
        lastOffsetTemp = offsetTemp;
        if (isGearAniming) return;
        offsetTemp = Math.max(offset * 1.0f - (height * 3 / 4), 0);
        A = offsetTemp * .8f < height / 4 ? offsetTemp : height / 4;
    }

    @Override
    public void onReleasing(float percent, int offset, int height, int extendHeight) {
        if (mState != RefreshState.Refreshing && mState != RefreshState.RefreshReleased) {
            onPulling(percent, offset, height, extendHeight);
        }
    }

    @Override
    public void onReleased(RefreshLayout refreshLayout, int height, int extendHeight) {
        lastOffsetTemp = offsetTemp;
        DecelerateInterpolator interpolator = new DecelerateInterpolator();
        final float reboundHeight = Math.max(A * 0.8f, height / 4 * scaleA);
        ValueAnimator waveAnimator = ValueAnimator.ofFloat(
                A, height / 4 * scaleA,
                (height / 2 - reboundHeight), height / 4 * scaleA,
                (reboundHeight * 0.4f), height / 4 * scaleA);
        waveAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                A = (float) animation.getAnimatedValue();
                postInvalidate();
            }
        });
        waveAnimator.setInterpolator(interpolator);
        waveAnimator.setDuration(500);
        waveAnimator.start();

        ValueAnimator gearAnimator = ValueAnimator.ofFloat(
                offsetTemp, mViewHeight*0.75F);
        gearAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                offsetTemp = (float) animation.getAnimatedValue();
                if (lastOffsetTemp>offsetTemp){
                    offsetTemp = lastOffsetTemp;
                    lastOffsetTemp = (float) animation.getAnimatedValue();
                }
            }
        });
        gearAnimator.setInterpolator(new LinearInterpolator());
        gearAnimator.setDuration(300);
        gearAnimator.start();

    }

    @Override
    public void onStartAnimator(@NonNull RefreshLayout refreshLayout, int height, int extendHeight) {
        waveAnimator.start();
        gearAnimator.start();
    }

    @Override
    public int onFinish(@NonNull RefreshLayout refreshLayout, boolean success) {
        gearAnimator.cancel();
        startFinishAnim();
        return 500;
    }

    private void startFinishAnim() {
        ValueAnimator animator = ValueAnimator.ofFloat(Base_R, Base_R * 1.1F, Base_R, Base_R * 0.7F);
        animator.setDuration(400);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Base_R = (float) animation.getAnimatedValue();
                postInvalidate();
            }
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                Base_R = dp2px(R_DP);
                waveAnimator.cancel();
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        animator.start();
    }


    @Override
    public void onHorizontalDrag(float percentX, int offsetX, int offsetMax) {

    }

    @Override
    public boolean isSupportHorizontalDrag() {
        return false;
    }

    @Override
    public void onStateChanged(RefreshLayout refreshLayout, RefreshState oldState, RefreshState newState) {
        mState = newState;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    }


    public static int dp2px(float dpValue) {
        return (int) (0.5f + dpValue * Resources.getSystem().getDisplayMetrics().density);
    }
}
