package ua.jenshensoft.cardslayout.views.updater.layout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.support.annotation.CallSuper;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.jenshen.awesomeanimation.AwesomeAnimation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import ua.jenshensoft.cardslayout.CardInfo;
import ua.jenshensoft.cardslayout.R;
import ua.jenshensoft.cardslayout.listeners.OnCardPercentageChangeListener;
import ua.jenshensoft.cardslayout.listeners.OnCardSwipedListener;
import ua.jenshensoft.cardslayout.listeners.OnCardTranslationListener;
import ua.jenshensoft.cardslayout.provider.CardCoordinates;
import ua.jenshensoft.cardslayout.provider.CardsCoordinatesProvider;
import ua.jenshensoft.cardslayout.util.DrawableUtils;
import ua.jenshensoft.cardslayout.util.FlagManager;
import ua.jenshensoft.cardslayout.util.SwipeGestureManager;
import ua.jenshensoft.cardslayout.views.CardView;
import ua.jenshensoft.cardslayout.views.card.Card;

import static ua.jenshensoft.cardslayout.views.updater.layout.CardsLayout.CardsDirection.LEFT_TO_RIGHT;
import static ua.jenshensoft.cardslayout.views.updater.layout.CardsLayout.CardsDirection.RIGHT_TO_LEFT;
import static ua.jenshensoft.cardslayout.views.updater.layout.CardsLayout.CircleCenterLocation.BOTTOM;
import static ua.jenshensoft.cardslayout.views.updater.layout.CardsLayout.CircleCenterLocation.TOP;
import static ua.jenshensoft.cardslayout.views.updater.layout.CardsLayout.DistributeCardsBy.CIRCLE;
import static ua.jenshensoft.cardslayout.views.updater.layout.CardsLayout.DistributeCardsBy.LINE;


public abstract class CardsLayout<Entity> extends FrameLayout
        implements
        OnCardTranslationListener<Entity>,
        OnCardSwipedListener<Entity>,
        OnCardPercentageChangeListener<Entity> {

    public static final int EMPTY = -1;

    //animation params
    @Nullable
    protected Interpolator interpolator;
    protected OnCreateAnimatorAction<Entity> defaultAnimatorAction;

    //property
    @LinearLayoutCompat.OrientationMode
    private int childListOrientation;
    @CardsDirection
    private int cardsLayout_cardsDirection;

    private int childListPaddingLeft;
    private int childListPaddingRight;
    private int childListPaddingTop;
    private int childListPaddingBottom;
    private int childList_height;
    private int childList_width;
    private int childList_circleRadius;
    private int durationOfAnimation;
    //distribution
    @DistributeCardsBy
    private int childList_distributeCardsBy;
    @CircleCenterLocation
    private int childList_circleCenterLocation;
    //card size
    private boolean fixedCardMeasure;
    private int cardWidth;
    private int cardHeight;
    private List<CardView<Entity>> cardViewList;
    private FlagManager gravityFlag;
    @Nullable
    private ColorFilter colorFilter;

    //listeners
    private OnCardSwipedListener<Entity> onCardSwipedListener;
    private OnCardPercentageChangeListener<Entity> onCardPercentageChangeListener;
    private OnCardTranslationListener<Entity> onCardTranslationListener;

    private boolean animateOnMeasure;
    private boolean enableValidatePositions = true;

    @Nullable
    private Animator animator;

    public CardsLayout(Context context) {
        super(context);
        init();
        if (!isInEditMode()) {
            inflateAttributes(context, null);
        }
    }

    public CardsLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        if (!isInEditMode()) {
            inflateAttributes(context, attrs);
        }
    }

    public CardsLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
        if (!isInEditMode()) {
            inflateAttributes(context, attrs);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public CardsLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
        if (!isInEditMode()) {
            inflateAttributes(context, attrs);
        }
    }


    /* lifecycle */

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (enableValidatePositions) {
            invalidateCardsPosition(animateOnMeasure);
            animateOnMeasure = false;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        if (child instanceof CardView) {
            setUpCardView((CardView<Entity>) child);
        } else {
            ((ViewGroup) child.getParent()).removeView(child);
            addCardView(child);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        if (child instanceof CardView) {
            CardView<Entity> cardView = (CardView<Entity>) child;
            cardView.setCardTranslationListener(null);
            cardView.setCardSwipedListener(null);
            cardView.setCardPercentageChangeListener(null, CardView.START_TO_CURRENT);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        clearAnimator();
    }

    /* public methods */

    public List<CardView<Entity>> getCardViews() {
        return cardViewList;
    }

    public void addCardView(View view, int position) {
        addCardViewToRootView(view, position);
    }

    public void addCardView(View view) {
        addCardViewToRootView(view);
    }

    public void removeCardView(int position) {
        CardView<Entity> cardView = findCardView(position);
        ViewParent parent = cardView.getParent();
        ((ViewGroup) parent).removeView(cardView);
        cardViewList.remove(cardView);
        for (CardView<Entity> view : cardViewList) {
            CardInfo<Entity> cardInfo = view.getCardInfo();
            int cardPosition = cardInfo.getCardPositionInLayout();
            if (cardPosition > position) {
                cardInfo.setCardPositionInLayout(cardPosition - 1);
            }
        }
        animateOnMeasure = true;
    }

    public void setIsTestMode() {
        if (childList_height != EMPTY) {
            childList_height += getContext().getResources().getDimensionPixelOffset(R.dimen.cardsLayout_test_card_offset);
        }

        if (childList_width != EMPTY) {
            childList_width += getContext().getResources().getDimensionPixelOffset(R.dimen.cardsLayout_test_card_offset);
        }
    }

    public boolean isEnableValidatePositions() {
        return enableValidatePositions;
    }

    public void setEnableValidatePositions(boolean enableValidatePositions) {
        this.enableValidatePositions = enableValidatePositions;
    }


    /* invalidate positions */

    @CallSuper
    @SuppressWarnings("ConstantConditions")
    public void invalidateCardsPosition() {
        invalidateCardsPosition(false, null, null);
    }

    @CallSuper
    @SuppressWarnings("ConstantConditions")
    public void invalidateCardsPosition(boolean withAnimation) {
        invalidateCardsPosition(withAnimation, null, null);
    }

    @CallSuper
    @SuppressWarnings("ConstantConditions")
    public void invalidateCardsPosition(boolean withAnimation,
                                        @NonNull OnCreateAnimatorAction<Entity> onCreateAnimatorAction) {
        invalidateCardsPosition(withAnimation, onCreateAnimatorAction, null);
    }

    @CallSuper
    @SuppressWarnings("ConstantConditions")
    public void invalidateCardsPosition(boolean withAnimation,
                                        @NonNull AnimatorListenerAdapter animatorListenerAdapter) {
        invalidateCardsPosition(withAnimation, null, animatorListenerAdapter);
    }

    @CallSuper
    public void invalidateCardsPosition(boolean withAnimation,
                                        @Nullable OnCreateAnimatorAction<Entity> onCreateAnimatorAction,
                                        @Nullable AnimatorListenerAdapter animatorListenerAdapter) {
        setViewsCoordinatesToStartPosition();
        moveViewsToStartPosition(withAnimation, onCreateAnimatorAction, animatorListenerAdapter);
    }

    
    /* listeners */

    public void setCardTranslationListener(OnCardTranslationListener<Entity> cardTranslationListener) {
        this.onCardTranslationListener = cardTranslationListener;
    }

    public void setCardPercentageChangeListener(OnCardPercentageChangeListener<Entity> onCardPercentageChangeListener) {
        this.onCardPercentageChangeListener = onCardPercentageChangeListener;
    }

    public void setOnCardSwipedListener(OnCardSwipedListener<Entity> onCardSwipedListener) {
        this.onCardSwipedListener = onCardSwipedListener;
    }
    
    
    /* property */

    @LinearLayoutCompat.OrientationMode
    public int getChildListOrientation() {
        return childListOrientation;
    }

    public void setChildListOrientation(@LinearLayoutCompat.OrientationMode int childListOrientation) {
        this.childListOrientation = childListOrientation;
    }

    public FlagManager getGravityFlag() {
        return gravityFlag;
    }

    public int getChildListPaddingBottom() {
        return childListPaddingBottom;
    }

    public void setChildListPaddingBottom(int childListPaddingBottom) {
        this.childListPaddingBottom = childListPaddingBottom;
    }

    public int getChildListPaddingLeft() {
        return childListPaddingLeft;
    }

    public void setChildListPaddingLeft(int childListPaddingLeft) {
        this.childListPaddingLeft = childListPaddingLeft;
    }

    public int getChildListPaddingRight() {
        return childListPaddingRight;
    }

    public void setChildListPaddingRight(int childListPaddingRight) {
        this.childListPaddingRight = childListPaddingRight;
    }

    public int getChildListPaddingTop() {
        return childListPaddingTop;
    }

    public void setChildListPaddingTop(int childListPaddingTop) {
        this.childListPaddingTop = childListPaddingTop;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setEnabledExceptViewsWithPositions(enabled);
    }

    public void setEnabledExceptPositions(boolean enabled, @Nullable int... position) {
        super.setEnabled(enabled);
        setEnabledExceptViewsWithPositions(enabled, position);
    }

    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        this.colorFilter = colorFilter;
    }

    /* animation property */

    public int getDurationOfAnimation() {
        return durationOfAnimation;
    }

    public void setDurationOfAnimation(int durationOfAnimation) {
        this.durationOfAnimation = durationOfAnimation;
    }

    public void setInterpolator(@NonNull Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    @Nullable
    public Interpolator getInterpolator() {
        return interpolator;
    }

    public OnCreateAnimatorAction<Entity> getDefaultCreateAnimatorAction() {
        return defaultAnimatorAction;
    }

    /* children size property */

    public boolean isFixedCardMeasure() {
        return fixedCardMeasure;
    }

    public void setFixedCardMeasure(boolean fixedCardMeasure) {
        this.fixedCardMeasure = fixedCardMeasure;
    }

    public int getCardWidth() {
        return cardWidth;
    }

    public void setCardWidth(int cardWidth) {
        this.cardWidth = cardWidth;
    }

    public int getCardHeight() {
        return cardHeight;
    }

    public void setCardHeight(int cardHeight) {
        this.cardHeight = cardHeight;
    }

    /* callbacks */

    @Override
    public void onCardTranslation(float positionX, float positionY, CardInfo<Entity> cardInfo, boolean isTouched) {
        if (onCardTranslationListener != null)
            onCardTranslationListener.onCardTranslation(positionX, positionY, cardInfo, isTouched);
    }

    @Override
    public void onCardSwiped(CardInfo<Entity> cardInfo) {
        if (onCardSwipedListener != null)
            onCardSwipedListener.onCardSwiped(cardInfo);
    }

    @Override
    public void onPercentageChanged(float percentageX, float percentageY, CardInfo<Entity> cardInfo, boolean isTouched) {
        if (onCardPercentageChangeListener != null) {
            onCardPercentageChangeListener.onPercentageChanged(percentageX, percentageY, cardInfo, isTouched);
        }
    }

    /* protected methods */

    protected void setViewsCoordinatesToStartPosition() {
        final List<CardView<Entity>> views = new ArrayList<>();
        for (CardView<Entity> cardView : cardViewList) {
            if (!shouldPassCard(cardView)) {
                views.add(cardView);
            }
        }
        final Config xConfig = getXConfiguration(views);
        final Config yConfig = getYConfiguration(views);

        if (childList_distributeCardsBy == LINE) {
            setXForViews(views, xConfig.getStartCoordinates(), xConfig.getDistanceBetweenViews());
            setYForViews(views, yConfig.getStartCoordinates(), yConfig.getDistanceBetweenViews());
            setRotationForViews(views);
        } else {
            if (childList_circleRadius == EMPTY) {
                throw new RuntimeException("You need to set radius");
            }
            final float cardsLayoutLength;
            if (childListOrientation == LinearLayoutCompat.HORIZONTAL) {
                cardsLayoutLength = xConfig.getDistanceForCards();
            } else {
                cardsLayoutLength = yConfig.getDistanceForCards();
            }
            final CardsCoordinatesProvider cardsCoordinatesProvider = new CardsCoordinatesProvider(
                    childListOrientation,
                    childList_circleCenterLocation,
                    getCardViewsCount(views),
                    childList_circleRadius,
                    getChildrenWidth(views),
                    getChildrenHeight(views),
                    cardsLayoutLength,
                    gravityFlag,
                    xConfig,
                    yConfig);
            final List<CardCoordinates> cardsCoordinates = cardsCoordinatesProvider.getCardsCoordinates();
            for (int i = 0; i < cardsCoordinates.size(); i++) {
                final CardView<Entity> cardView = views.get(i);
                final CardCoordinates cardCoordinates = cardsCoordinates.get(i);
                setXForView(cardView, cardCoordinates.getX());
                setYForView(cardView, cardCoordinates.getY());
                setRotation(cardView, cardCoordinates.getAngle());
            }
        }
    }

    protected void moveViewsToStartPosition(boolean withAnimation,
                                            @Nullable OnCreateAnimatorAction<Entity> animationCreateAction,
                                            @Nullable AnimatorListenerAdapter animatorListenerAdapter) {
        final List<CardView<Entity>> views = new ArrayList<>();
        for (CardView<Entity> cardView : cardViewList) {
            if (!shouldPassCard(cardView)) {
                views.add(cardView);
            }
        }
        final List<Animator> animators = new ArrayList<>();
        for (int i = 0; i < views.size(); i++) {
            CardView<Entity> cardView = views.get(i);
            CardInfo<Entity> cardInfo = cardView.getCardInfo();
            if (withAnimation) {
                final Animator animator;
                if (animationCreateAction != null) {
                    animator = animationCreateAction.createAnimation(cardView);
                } else {
                    animator = this.defaultAnimatorAction.createAnimation(cardView);
                }
                animators.add(animator);
            } else {
                cardView.setX(cardInfo.getFirstPositionX());
                cardView.setY(cardInfo.getFirstPositionY());
                cardView.setRotation(cardInfo.getFirstRotation());
            }
        }
        if (!animators.isEmpty()) {
            enableValidatePositions = false;
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    enableValidatePositions = true;
                    CardsLayout.this.animator = null;
                }
            });
            if (animatorListenerAdapter != null) {
                animatorSet.addListener(animatorListenerAdapter);
            }

            clearAnimator();
            CardsLayout.this.animator = animatorSet;
            animatorSet.start();
        }
    }

    protected <T extends View> Config getXConfiguration(List<T> views) {
        float rootWidth = getRootWidth();
        float widthOfViews = getWidthOfViews(views, 0);
        float difference = widthOfViews - rootWidth;
        float distanceBetweenViews = getDistanceBetweenViews(difference, views);

        if (difference > 0) {
            widthOfViews = getWidthOfViews(views, distanceBetweenViews);
        }
        float startXPositionFromList = getStartXPositionForList(getChildrenWidth(views), widthOfViews, rootWidth);
        return new Config(startXPositionFromList, distanceBetweenViews, widthOfViews);
    }

    protected <T extends View> Config getYConfiguration(List<T> views) {
        float rootHeight = getRootHeight();
        float heightOfViews = getHeightOfViews(views, 0);
        float difference = heightOfViews - rootHeight;
        float distanceBetweenViews = getDistanceBetweenViews(difference, views);

        if (difference > 0) {
            heightOfViews = getHeightOfViews(views, distanceBetweenViews);
        }
        float startYPositionFromList = getStartYPositionForList(getChildrenHeight(views), heightOfViews, rootHeight);
        return new Config(startYPositionFromList, distanceBetweenViews, heightOfViews);
    }

    protected <T extends View> float getDistanceBetweenViews(float difference, List<T> views) {
        if (difference > 0) {
            return difference / (getCardViewsCount(views) - 1f);
        } else {
            return 0;
        }
    }

    protected float getRootWidth() {
        int widthLayout = childList_width == EMPTY ? getMeasuredWidth() : childList_width;
        return widthLayout - getChildListPaddingRight() - getChildListPaddingLeft();
    }

    protected float getRootHeight() {
        int heightLayout = childList_height == EMPTY ? getMeasuredHeight() : childList_height;
        return heightLayout - getChildListPaddingBottom() - getChildListPaddingTop();
    }

    protected float getStartXPositionForList(float widthOfItem, float widthOfViews, float rootWidth) {
        float cardPositionX = 0;
        if (gravityFlag.containsFlag(FlagManager.Gravity.LEFT)) {
            cardPositionX = 0;
        } else if (gravityFlag.containsFlag(FlagManager.Gravity.RIGHT)) {
            if (childListOrientation == LinearLayoutCompat.HORIZONTAL) {
                cardPositionX = rootWidth - widthOfViews;
            } else {
                cardPositionX = rootWidth - widthOfItem;
            }
        } else if (gravityFlag.containsFlag(FlagManager.Gravity.CENTER_HORIZONTAL)
                || gravityFlag.containsFlag(FlagManager.Gravity.CENTER)) {
            if (childListOrientation == LinearLayoutCompat.HORIZONTAL) {
                cardPositionX = (rootWidth - widthOfViews) / 2f;
            } else {
                cardPositionX = rootWidth / 2f - widthOfItem / 2f;
            }
        }

        cardPositionX += getChildListPaddingLeft();
        if (childList_width != EMPTY) {
            cardPositionX += (getMeasuredWidth() - childList_width) / 2f;
        }
        return cardPositionX;
    }

    protected float getStartYPositionForList(float heightOfItem, float heightOfViews, float rootHeight) {
        float cardPositionY = 0;
        if (gravityFlag.containsFlag(FlagManager.Gravity.TOP)) {
            cardPositionY = 0;
        } else if (gravityFlag.containsFlag(FlagManager.Gravity.BOTTOM)) {
            if (childListOrientation == LinearLayoutCompat.VERTICAL) {
                cardPositionY = (rootHeight - heightOfViews);
            } else {
                cardPositionY = (rootHeight - heightOfItem);
            }
        } else if (gravityFlag.containsFlag(FlagManager.Gravity.CENTER_VERTICAL)
                || gravityFlag.containsFlag(FlagManager.Gravity.CENTER)) {
            if (childListOrientation == LinearLayoutCompat.VERTICAL) {
                cardPositionY = (rootHeight - heightOfViews) / 2f;
            } else {
                cardPositionY = rootHeight / 2f - heightOfItem / 2f;
            }
        }

        cardPositionY += getChildListPaddingTop();
        if (childList_height != EMPTY) {
            cardPositionY += (getMeasuredHeight() - childList_height) / 2f;
        }
        return cardPositionY;
    }

    protected <T extends View> float getChildrenWidth(@NonNull List<T> views) {
        float width = 0;
        if (views.isEmpty())
            return width;
        for (T view : views) {
            float currentWidth = getChildWidth(view);
            if (currentWidth > width) {
                width = currentWidth;
            }
        }
        return width;
    }

    protected <T extends View> float getChildrenHeight(@NonNull List<T> views) {
        float height = 0;
        if (views.isEmpty())
            return height;
        for (T view : views) {
            float currentHeight = getChildHeight(view);
            if (currentHeight > height) {
                height = currentHeight;
            }
        }
        return height;
    }

    protected int getChildWidth(View view) {
        if (fixedCardMeasure) {
            if (cardWidth == -1) {
                throw new RuntimeException("You should set the \"child width\" attr");
            }
            return cardWidth;
        } else {
            return view.getMeasuredWidth();
        }
    }

    protected int getChildHeight(View view) {
        if (fixedCardMeasure) {
            if (cardHeight == -1) {
                throw new RuntimeException("You should set the \"child height\" attr");
            }
            return cardHeight;
        } else {
            return view.getMeasuredHeight();
        }
    }

    protected <T extends View> float getWidthOfViews(@NonNull List<T> views, float offset) {
        float widthOfViews = 0;
        for (T view : views) {
            if (shouldPassView(view)) {
                continue;
            }
            widthOfViews += getChildWidth(view) - offset;
        }
        widthOfViews += offset;
        return widthOfViews;
    }

    protected <T extends View> float getHeightOfViews(@NonNull List<T> views, float offset) {
        float heightViews = 0;
        for (T view : views) {
            if (shouldPassView(view)) {
                continue;
            }
            heightViews += getChildHeight(view) - offset;
        }
        heightViews += offset;
        return heightViews;
    }

    protected void setXForViews(@NonNull List<CardView<Entity>> views, float cardPositionX, float distanceBetweenViews) {
        float x = cardPositionX;
        for (CardView<Entity> view : views) {
            if (shouldPassCard(view)) {
                continue;
            }
            setXForView(view, x);
            if (childListOrientation == LinearLayout.HORIZONTAL)
                x += getChildWidth(view) - distanceBetweenViews;
        }
    }

    protected void setYForViews(@NonNull List<CardView<Entity>> views, float cardPositionY, float distanceBetweenViews) {
        float y = cardPositionY;
        for (CardView<Entity> view : views) {
            if (shouldPassCard(view)) {
                continue;
            }
            setYForView(view, y);
            if (childListOrientation == LinearLayout.VERTICAL)
                y += getChildHeight(view) - distanceBetweenViews;
        }
    }

    protected <C extends View & Card<Entity>> void setRotationForViews(@NonNull List<C> views) {
        for (C view : views) {
            if (shouldPassCard(view)) {
                continue;
            }
            setRotation(view, 0);
        }
    }

    protected void setXForView(Card<Entity> cardView, float cardPositionX) {
        CardInfo<Entity> cardInfo = cardView.getCardInfo();
        cardInfo.setFirstPositionX(Math.round(cardPositionX));
    }

    protected void setYForView(Card<Entity> cardView, float cardPositionY) {
        CardInfo<Entity> cardInfo = cardView.getCardInfo();
        cardInfo.setFirstPositionY(Math.round(cardPositionY));
    }

    protected void setRotation(Card<Entity> cardView, float rotation) {
        CardInfo<Entity> cardInfo = cardView.getCardInfo();
        cardInfo.setFirstRotation(Math.round(rotation));
    }


    /* private methods */

    private void init() {
        //attr
        childListOrientation = LinearLayoutCompat.HORIZONTAL;
        cardsLayout_cardsDirection = CardsDirection.LEFT_TO_RIGHT;
        childListPaddingLeft = 0;
        childListPaddingRight = 0;
        childListPaddingTop = 0;
        childListPaddingBottom = 0;
        childList_height = EMPTY;
        childList_width = EMPTY;
        childList_circleRadius = EMPTY;
        durationOfAnimation = 500;
        //distribution
        childList_distributeCardsBy = LINE;
        childList_circleCenterLocation = BOTTOM;
        gravityFlag = new FlagManager(FlagManager.Gravity.BOTTOM);
        cardViewList = new ArrayList<>();
        defaultAnimatorAction = new OnCreateAnimatorAction<Entity>() {
            @Override
            public <C extends View & Card<Entity>> Animator createAnimation(C cardView) {
                AwesomeAnimation.Builder awesomeAnimation = new AwesomeAnimation.Builder(cardView)
                        .setX(AwesomeAnimation.CoordinationMode.COORDINATES, cardView.getCardInfo().getCurrentPositionX(), cardView.getCardInfo().getFirstPositionX())
                        .setY(AwesomeAnimation.CoordinationMode.COORDINATES, cardView.getCardInfo().getCurrentPositionY(), cardView.getCardInfo().getFirstPositionY())
                        .setRotation(cardView.getRotation(), cardView.getCardInfo().getFirstRotation())
                        .setDuration(durationOfAnimation);
                if (interpolator != null)
                    awesomeAnimation.setInterpolator(interpolator);
                return awesomeAnimation.build().getAnimatorSet();
            }
        };
    }

    @SuppressWarnings("WrongConstant")
    private void inflateAttributes(Context context, @Nullable AttributeSet attributeSet) {
        if (attributeSet != null) {
            TypedArray attributes = context.obtainStyledAttributes(attributeSet, R.styleable.CardsLayout_Params);
            try {
                cardsLayout_cardsDirection = attributes.getInt(R.styleable.CardsLayout_Params_cardsLayout_cardsDirection, cardsLayout_cardsDirection);
                gravityFlag = new FlagManager(attributes.getInt(R.styleable.CardsLayout_Params_cardsLayout_cardsGravity, FlagManager.Gravity.CENTER));
                childListOrientation = attributes.getInt(R.styleable.CardsLayout_Params_cardsLayout_childList_orientation, childListOrientation);
                durationOfAnimation = attributes.getInt(R.styleable.CardsLayout_Params_cardsLayout_animationDuration, durationOfAnimation);
                childListPaddingLeft = attributes.getDimensionPixelOffset(R.styleable.CardsLayout_Params_cardsLayout_childList_paddingLeft, childListPaddingLeft);
                childListPaddingRight = attributes.getDimensionPixelOffset(R.styleable.CardsLayout_Params_cardsLayout_childList_paddingRight, childListPaddingRight);
                childListPaddingTop = attributes.getDimensionPixelOffset(R.styleable.CardsLayout_Params_cardsLayout_childList_paddingTop, childListPaddingTop);
                childListPaddingBottom = attributes.getDimensionPixelOffset(R.styleable.CardsLayout_Params_cardsLayout_childList_paddingBottom, childListPaddingBottom);
                childList_height = attributes.getDimensionPixelOffset(R.styleable.CardsLayout_Params_cardsLayout_childList_height, childList_height);
                childList_width = attributes.getDimensionPixelOffset(R.styleable.CardsLayout_Params_cardsLayout_childList_width, childList_width);
                final int color = attributes.getColor(R.styleable.CardsLayout_Params_cardsLayout_tintColor, -1);
                if (color != -1) {
                    colorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
                }

                //distribution
                childList_distributeCardsBy = attributes.getInt(R.styleable.CardsLayout_Params_cardsLayout_childList_distributeCardsBy, childList_distributeCardsBy);
                childList_circleRadius = attributes.getDimensionPixelOffset(R.styleable.CardsLayout_Params_cardsLayout_childList_circleRadius, childList_circleRadius);
                childList_circleCenterLocation = attributes.getInt(R.styleable.CardsLayout_Params_cardsLayout_childList_circleCenterLocation, childList_circleCenterLocation);

                //card measure
                fixedCardMeasure = attributes.getBoolean(R.styleable.CardsLayout_Params_cardsLayout_fixedCardMeasure, false);
                cardWidth = attributes.getDimensionPixelOffset(R.styleable.CardsLayout_Params_cardsLayout_cardWidth, -1);
                cardHeight = attributes.getDimensionPixelOffset(R.styleable.CardsLayout_Params_cardsLayout_cardHeight, -1);
            } finally {
                attributes.recycle();
            }
        }
    }


    /* card view methods */

    private CardView<Entity> createCardView(View view) {
        CardView<Entity> cardView = new CardView<>(getContext());
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardView.setLayoutParams(layoutParams);
        cardView.addView(view);
        return cardView;
    }

    private void setUpCardView(CardView<Entity> cardView) {
        cardView.setSwipeOrientationMode(SwipeGestureManager.OrientationMode.BOTH);
        cardView.setCardTranslationListener(this);
        cardView.setCardSwipedListener(this);
        cardView.setCardPercentageChangeListener(this, CardView.START_TO_CURRENT);
        if (cardsLayout_cardsDirection == LEFT_TO_RIGHT) {
            cardViewList.add(cardView);
        } else {
            cardViewList.add(0, cardView);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cardView.setElevation(getResources().getDimensionPixelOffset(R.dimen.cardsLayout_card_elevation_normal));
        }
    }

    private void addCardViewToRootView(View view) {
        CardView<Entity> cardView = createCardView(view);
        cardView.setCardInfo(new CardInfo<>(cardViewList.size()));
        this.addView(cardView);
    }

    private void addCardViewToRootView(View view, int position) {
        CardView<Entity> cardView = createCardView(view);
        cardView.setCardInfo(new CardInfo<>(cardViewList.size()));
        this.addView(cardView);
        cardViewList.add(position, cardView);
    }

    private CardView<Entity> findCardView(int position) {
        for (CardView<Entity> cardView : cardViewList) {
            if (cardView.getCardInfo().getCardPositionInLayout() == position) {
                return cardView;
            }
        }
        throw new RuntimeException("Can't find view");
    }

    private <T extends View> int getCardViewsCount(@NonNull List<T> views) {
        int count = 0;
        for (T view : views) {
            if (shouldPassView(view)) {
                continue;
            }
            count++;
        }
        return count;
    }

    private void setEnabledExceptViewsWithPositions(boolean state, @Nullable int... positions) {
        List<Integer> positionsList = null;
        if (positions != null) {
            positionsList = new ArrayList<>();
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < positions.length; i++) {
                positionsList.add(positions[i]);
            }
        }
        for (CardView cardView : cardViewList) {
            if (state) {
                if (!cardView.isEnabled()) {
                    DrawableUtils.setColorFilter(cardView, null);
                }
                cardView.setEnabled(true);
            } else {
                if (cardView.getCardInfo() == null ||
                        (positionsList != null && !positionsList.contains(cardView.getCardInfo().getCardPositionInLayout()))) {
                    if (cardView.isEnabled() && colorFilter != null) {
                        DrawableUtils.setColorFilter(cardView, colorFilter);
                    }
                    cardView.setEnabled(false);
                }
            }
        }
    }

    //// TODO: 4/19/17
    private boolean shouldPassView(View view) {
        return view.getVisibility() != VISIBLE ||
                CardView.class.isInstance(view) && !((CardView) view).getCardInfo().isCardDistributed();
    }

    private <C extends View & Card<Entity>> boolean shouldPassCard(C card) {
        return card.getVisibility() != VISIBLE || !card.getCardInfo().isCardDistributed();
    }

    private void clearAnimator() {
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
            clearAnimation();
        }
    }

    /* inner types */

    @FunctionalInterface
    public interface OnCreateAnimatorAction<Entity> {
        <C extends View & Card<Entity>> Animator createAnimation(C cardView);
    }

    @IntDef({TOP, BOTTOM})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CircleCenterLocation {
        int TOP = 0;
        int BOTTOM = 1;
    }

    @IntDef({LINE, CIRCLE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DistributeCardsBy {
        int LINE = 0;
        int CIRCLE = 1;
    }

    @IntDef({LEFT_TO_RIGHT, RIGHT_TO_LEFT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CardsDirection {
        int LEFT_TO_RIGHT = 0;
        int RIGHT_TO_LEFT = 1;
    }
}