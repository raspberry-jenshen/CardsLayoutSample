package ua.jenshensoft.cardslayout.views.table;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.ColorFilter;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.android.internal.util.Predicate;
import com.jenshen.awesomeanimation.AwesomeAnimation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import ua.jenshensoft.cardslayout.CardInfo;
import ua.jenshensoft.cardslayout.R;
import ua.jenshensoft.cardslayout.listeners.table.OnCardClickListener;
import ua.jenshensoft.cardslayout.listeners.table.OnDistributedCardsListener;
import ua.jenshensoft.cardslayout.pattern.CardDeckCoordinatesPattern;
import ua.jenshensoft.cardslayout.pattern.models.ThreeDCardCoordinates;
import ua.jenshensoft.cardslayout.util.DistributionState;
import ua.jenshensoft.cardslayout.util.FlagManager;
import ua.jenshensoft.cardslayout.views.ViewUpdateConfig;
import ua.jenshensoft.cardslayout.views.card.Card;
import ua.jenshensoft.cardslayout.views.layout.CardDeckView;
import ua.jenshensoft.cardslayout.views.layout.CardsLayout;
import ua.jenshensoft.cardslayout.views.updater.ViewUpdater;
import ua.jenshensoft.cardslayout.views.updater.callback.OnViewParamsUpdate;
import ua.jenshensoft.cardslayout.views.updater.model.GameTableParams;

@SuppressWarnings("unused")
public abstract class GameTableLayout<
        Layout extends CardsLayout>
        extends ViewGroup
        implements OnViewParamsUpdate<GameTableParams> {

    public static final float EPSILON = 0.00000001f;

    //Views
    protected List<Layout> cardsLayouts;
    protected List<Card> cardDeckCards = new ArrayList<>();
    @Nullable
    protected CardDeckView cardDeckView;
    //updaters
    protected ViewUpdater<GameTableParams> viewUpdater;
    protected ViewUpdateConfig viewUpdateConfig;
    //attr
    private int durationOfDistributeAnimation = 1000;
    private boolean isEnableSwipe;
    private boolean isEnableTransition;
    private int currentPlayerLayoutId = -1;
    //card deck attr
    private boolean canAutoDistribute = true;
    private boolean deskOfCardsEnable = true;
    private FlagManager cardDeckGravity;
    private float cardDeckCardOffsetX = -1;
    private float cardDeckCardOffsetY = -1;
    private float cardDeckCardOffsetZ = -1;
    //listeners
    @Nullable
    private OnCardClickListener onCardClickListener;
    @Nullable
    private OnDistributedCardsListener onDistributedCardsListener;
    private boolean onDistributeAnimation;

    public GameTableLayout(Context context) {
        super(context);
        if (!isInEditMode()) {
            initLayout(null);
        }
        inflateLayout();
    }

    public GameTableLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            initLayout(attrs);
        }
        inflateLayout();
    }

    public GameTableLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (!isInEditMode()) {
            initLayout(attrs);
        }
        inflateLayout();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public GameTableLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (!isInEditMode()) {
            initLayout(attrs);
        }
        inflateLayout();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        if (child instanceof CardsLayout) {
            Layout layout = (Layout) child;
            cardsLayouts.add(layout);
            if (currentPlayerLayoutId != -1 && currentPlayerLayoutId == child.getId()) {
                if (isEnableSwipe) {
                    setSwipeValidatorEnabled(layout);
                }
                if (isEnableTransition) {
                    setPercentageValidatorEnabled(layout);
                }
            }
        } else if (child instanceof CardDeckView) {
            cardDeckView = (CardDeckView) child;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // Find out how big everyone wants to be
        measureChildren(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                if (child instanceof CardsLayout) {
                    CardsLayout layout = (CardsLayout) child;
                    child.layout(0, 0, layout.getMeasuredWidth(), layout.getMeasuredHeight());
                } else if (child instanceof CardDeckView) {
                    CardDeckView cardDeckView = (CardDeckView) child;
                    float cardDeckX = getXPositionForCardDeck(cardDeckView.getMeasuredWidth(), getMeasuredWidth());
                    float cardDeckY = getYPositionForCardDeck(cardDeckView.getMeasuredHeight(), getMeasuredHeight());
                    child.layout(
                            Math.round(cardDeckX),
                            Math.round(cardDeckY),
                            Math.round(cardDeckX) + cardDeckView.getMeasuredWidth(),
                            Math.round(cardDeckY) + cardDeckView.getMeasuredHeight());
                }
            }
        }
        onLayoutCardDeck(changed);
        viewUpdater.onViewUpdated();
    }

    /* view updater */

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onUpdateViewParams(GameTableParams params, boolean calledInOnMeasure) {
        if (hasDistributionState()) {
            DistributionState distributionState = params.getDistributionState();
            if (!onDistributeAnimation && !distributionState.isCardsAlreadyDistributed() && canAutoDistribute) {
                startDistributeCards();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        viewUpdater.clear();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (Layout cardsLayout : cardsLayouts) {
            cardsLayout.setEnabled(enabled);
        }
    }

    /* public methods */

    public void setEnabled(boolean enabled, @Nullable ColorFilter colorFilter) {
        super.setEnabled(enabled);
        for (Layout cardsLayout : cardsLayouts) {
            cardsLayout.setEnabledCards(enabled, colorFilter, null);
        }
    }

    public boolean isCardDragged() {
        for (Layout layout : cardsLayouts) {
            if (layout.isCardDragged()) {
                return true;
            }
        }
        return false;
    }

    public void setOnCardClickListener(@Nullable OnCardClickListener onCardClickListener) {
        this.onCardClickListener = onCardClickListener;
    }

    public void setOnDistributedCardsListener(@Nullable OnDistributedCardsListener onDistributedCardsListener) {
        this.onDistributedCardsListener = onDistributedCardsListener;
    }

    public void updateDistributionState(@Nullable DistributionState distributionState) {
        viewUpdater.setParams(new GameTableParams(distributionState));
        setCardDeckCards();
    }

    public int getCurrentPlayerLayoutId() {
        return currentPlayerLayoutId;
    }

    public void setCurrentPlayerLayoutId(int currentPlayerLayoutId) {
        this.currentPlayerLayoutId = currentPlayerLayoutId;
    }

    public Layout getCurrentPlayerCardsLayout() {
        if (currentPlayerLayoutId != -1) {
            for (Layout layout : cardsLayouts) {
                if (layout.getId() == currentPlayerLayoutId) {
                    return layout;
                }
            }
        }
        throw new RuntimeException("Can't find the current player layout");
    }

    public void setSwipeValidatorEnabled(final Layout cardsLayout) {
        cardsLayout.addOnCardSwipedListener(cardInfo -> {
            if (!cardsLayout.isEnabled()) {
                cardsLayout.setEnabled(true);
            }
            onActionWithCard(cardInfo);
        });
    }

    public void setPercentageValidatorEnabled(final Layout cardsLayout) {
        cardsLayout.addCardPercentageChangeListener((percentageX, percentageY, cardInfo, isTouched) -> {
            if (!isTouched) {
                if (!cardsLayout.isEnabled()) {
                    cardsLayout.setEnabled(true);
                }
                if (percentageX >= 100 || percentageY >= 100) {
                    onActionWithCard(cardInfo);
                }
            } else {
                if (cardsLayout.isEnabled()) {
                    cardsLayout.setEnabledCards(false, Collections.singletonList(cardInfo.getCardPositionInLayout()));
                }
            }
        });
    }

    public int getDurationOfDistributeAnimation() {
        return durationOfDistributeAnimation;
    }

    public void setDurationOfDistributeAnimation(int durationOfDistributeAnimation) {
        this.durationOfDistributeAnimation = durationOfDistributeAnimation;
    }

    @SuppressWarnings("ConstantConditions")
    public void setCardDeckCards() {
        if (!hasDistributionState()) {
            throw new RuntimeException("You need to set distribution state before");
        }
        DistributionState distributionState = viewUpdater.getParams().getDistributionState();
        if (distributionState.isCardsAlreadyDistributed()) {
            Predicate<Card> predicateForCardsOnTheHands = entityCard ->
                    distributionState.getCardsPredicateBeforeDistribution().apply(entityCard) ||
                            distributionState.getCardsPredicateForDistribution().apply(entityCard);
            setCardDeckCards(predicateForCardsOnTheHands);
        } else {
            setCardDeckCards(distributionState.getCardsPredicateBeforeDistribution());
        }
    }

    private void setCardDeckCard(Card card, Predicate<Card> predicateForCardsOnTheHands) {
        if (predicateForCardsOnTheHands.apply(card)) {
            if (deskOfCardsEnable) {
                card.getCardInfo().setCardDistributed(true);
            } else {
                card.setVisibility(VISIBLE);
            }
        } else {
            if (deskOfCardsEnable) {
                CardInfo cardInfo = card.getCardInfo();
                cardInfo.setCardDistributed(false);
            } else {
                card.setVisibility(INVISIBLE);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cardDeckCards.add(0, card);
            } else {
                cardDeckCards.add(card);
            }
        }
    }

    public void setCardDeckCards(Predicate<Card> predicateForCardsOnTheHands) {
        for (Layout layout : cardsLayouts) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (int i = layout.getCards().size() - 1; i >= 0; i--) {
                    Card card = layout.getCards().get(i);
                    setCardDeckCard(card, predicateForCardsOnTheHands);
                }
            } else {
                for (Card card : layout.getCards()) {
                    setCardDeckCard(card, predicateForCardsOnTheHands);
                }
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    public void startDistributeCards() {
        if (!hasDistributionState()) {
            throw new RuntimeException("You need to set distribution state before");
        }
        viewUpdater.addAction(
                calledInOnMeasure -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        postOnAnimation(() -> startDistributeCards(viewUpdater.getParams().getDistributionState().getCardsPredicateForDistribution()));
                    } else {
                        postDelayed(() -> startDistributeCards(viewUpdater.getParams().getDistributionState().getCardsPredicateForDistribution()), 300);
                    }
                });
    }

    public void startDistributeCards(Predicate<Card> predicate) {
        onDistributeAnimation = true;
        setEnabled(false, null);//disable cards without filters
        TableDistributedCardsListener distributedCardsListener = new TableDistributedCardsListener(cardsLayouts.size(), new OnDistributedCardsListener() {
            @Override
            public void onDistributedCards() {
                GameTableLayout.this.onDistributedCards();
            }

            @Override
            public void onStartDistributedCardWave(List<Card> cards) {
                GameTableLayout.this.onStartDistributedCardWave(cards);
            }

            @Override
            public void onEndDistributeCardWave(List<Card> cards) {
                GameTableLayout.this.onEndDistributeCardWave(cards);
            }
        });
        for (Layout cardsLayout : cardsLayouts) {
            distributeCardForPlayer(
                    cardsLayout,
                    predicate,
                    distributedCardsListener);
        }
    }

    @SuppressWarnings("ConstantConditions")
    protected void onDistributedCards() {
        setEnabled(true);
        onDistributeAnimation = false;
        if (hasDistributionState()) {
            DistributionState distributionState = viewUpdater.getParams().getDistributionState();
            distributionState.setCardsAlreadyDistributed(true);
        }
        if (GameTableLayout.this.onDistributedCardsListener != null) {
            GameTableLayout.this.onDistributedCardsListener.onDistributedCards();
        }
    }


    /* protected methods */

    protected void onStartDistributedCardWave(List<Card> cards) {
        if (GameTableLayout.this.onDistributedCardsListener != null) {
            GameTableLayout.this.onDistributedCardsListener.onStartDistributedCardWave(cards);
        }
    }

    protected void onEndDistributeCardWave(List<Card> cards) {
        //set elevation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (Card card : cards) {
                card.setCardZ(card.getNormalElevation());
            }
        }
        cardDeckCards.removeAll(cards);
        if (GameTableLayout.this.onDistributedCardsListener != null) {
            GameTableLayout.this.onDistributedCardsListener.onEndDistributeCardWave(cards);
        }
    }

    protected CardsLayout.OnCreateAnimatorAction creteAnimationForCardDistribution(Layout cardsLayout,
                                                                                           Card card) {
        return new CardsLayout.OnCreateAnimatorAction() {
            @Override
            public <C extends View & Card> Animator createAnimation(C view) {
                if (view.equals(card)) {
                    AwesomeAnimation.Builder awesomeAnimation = new AwesomeAnimation.Builder(view)
                            .setX(AwesomeAnimation.CoordinationMode.COORDINATES, view.getX(), view.getCardInfo().getFirstPositionX())
                            .setY(AwesomeAnimation.CoordinationMode.COORDINATES, view.getY(), view.getCardInfo().getFirstPositionY())
                            .setRotation(180, card.getCardInfo().getFirstRotation())
                            .setDuration(durationOfDistributeAnimation);
                    if (cardsLayout.getInterpolator() != null) {
                        awesomeAnimation.setInterpolator(cardsLayout.getInterpolator());
                    }
                    return awesomeAnimation.build().getAnimatorSet();
                } else {
                    return cardsLayout.getDefaultCreateAnimatorAction().createAnimation(view);
                }
            }
        };
    }

    protected  <CV extends View & Card> void onLayoutCardInCardDeck(CV card, ThreeDCardCoordinates coordinates) {
        int x;
        int y;
        if (card.getCardInfo().isCardDistributed()) {
            x = Math.round(card.getX());
            y = Math.round(card.getY());
        } else {
            if ((Math.abs(coordinates.getX() - (card.getX())) < EPSILON) &&
                    (Math.abs(coordinates.getY() - (card.getY())) < EPSILON)) {
                return;
            }
            x = Math.round(coordinates.getX());
            y = Math.round(coordinates.getY());
            int angle = Math.round(coordinates.getAngle());
            card.setRotation(angle);
            card.setCardZ(coordinates.getZ());
            card.setFirstX(x);
            card.setFirstY(y);
            card.setFirstRotation(angle);
        }
        card.layout(x, y, x + card.getMeasuredWidth(), y + card.getMeasuredHeight());
    }

    /* private methods */

    @SuppressWarnings({"unchecked"})
    private void initLayout(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray attributesTable = getContext().obtainStyledAttributes(attrs, R.styleable.GameTableLayout);
            TypedArray attributes = getContext().obtainStyledAttributes(attrs, R.styleable.CardDeckView);
            try {
                durationOfDistributeAnimation = attributesTable.getInteger(R.styleable.GameTableLayout_gameTableLayout_duration_distributeAnimation, durationOfDistributeAnimation);
                isEnableSwipe = attributesTable.getBoolean(R.styleable.GameTableLayout_gameTableLayout_cardValidatorSwipe, isEnableSwipe);
                isEnableTransition = attributesTable.getBoolean(R.styleable.GameTableLayout_gameTableLayout_cardValidatorTransition, isEnableTransition);
                currentPlayerLayoutId = attributesTable.getResourceId(R.styleable.GameTableLayout_gameTableLayout_currentPlayerLayoutId, currentPlayerLayoutId);

                //card deck
                canAutoDistribute = attributesTable.getBoolean(R.styleable.GameTableLayout_gameTableLayout_canAutoDistribute, canAutoDistribute);
                deskOfCardsEnable = attributesTable.getBoolean(R.styleable.GameTableLayout_gameTableLayout_cardDeckEnable, deskOfCardsEnable);
                cardDeckGravity = new FlagManager(attributesTable.getInt(R.styleable.GameTableLayout_gameTableLayout_cardDeckGravity, FlagManager.Gravity.CENTER));

                cardDeckCardOffsetX = attributes.getDimension(R.styleable.CardDeckView_cardDeck_cardDeck_cardOffset_x, cardDeckCardOffsetX);
                cardDeckCardOffsetY = attributes.getDimension(R.styleable.CardDeckView_cardDeck_cardDeck_cardOffset_y, cardDeckCardOffsetY);
                cardDeckCardOffsetZ = attributes.getDimension(R.styleable.CardDeckView_cardDeck_cardDeck_cardOffset_z, cardDeckCardOffsetZ);
            } finally {
                attributesTable.recycle();
                attributes.recycle();
            }
        }
    }

    private void inflateLayout() {
        cardsLayouts = new ArrayList<>();
        viewUpdater = new ViewUpdater<>(this);
        viewUpdateConfig = new ViewUpdateConfig(this);
        cardDeckGravity = new FlagManager(FlagManager.Gravity.CENTER);
        if (Math.abs(cardDeckCardOffsetX - (-1)) < EPSILON) {
            cardDeckCardOffsetX = getResources().getDimension(R.dimen.cardsLayout_shadow_desk_offset);
        }
        if (Math.abs(cardDeckCardOffsetY - (-1)) < EPSILON) {
            cardDeckCardOffsetY = getResources().getDimension(R.dimen.cardsLayout_shadow_desk_offset);
        }
        if (Math.abs(cardDeckCardOffsetZ - (-1)) < EPSILON) {
            cardDeckCardOffsetZ = getResources().getDimension(R.dimen.cardsLayout_shadow_desk_offset);
        }
    }

    @SuppressWarnings("unchecked")
    private <CV extends View & Card> void onLayoutCardDeck(boolean changed) {
        if (cardDeckCards.isEmpty()) {
            return;
        }
        if (viewUpdateConfig.needUpdateViewOnLayout(changed)) {
            float cardDeckX;
            float cardDeckY;
            float cardDeckZ;
            if (cardDeckView != null && !cardDeckView.getCardsCoordinates().isEmpty()) {
                List<ThreeDCardCoordinates> cardsCoordinates = cardDeckView.getCardsCoordinates();
                if (cardsCoordinates == null) {
                    throw new RuntimeException("Something went wrong, coordinates can't be null");
                }
                ThreeDCardCoordinates lastCardCoordinates = cardsCoordinates.get(cardsCoordinates.size() - 1);
                cardDeckX = cardDeckView.getX() + lastCardCoordinates.getX() + cardDeckView.getPaddingLeft();
                cardDeckY = cardDeckView.getY() + lastCardCoordinates.getY() + cardDeckView.getPaddingTop();
                cardDeckZ = 0;
                for (Card card : cardDeckCards) {
                    float cardZ = card.getCardZ();
                    if (cardZ > cardDeckZ) {
                        cardDeckZ = cardZ;
                    }
                }
            } else {
                float maxWidth = 0;
                float maxHeight = 0;
                float maxElevation = 0;
                for (Card card : cardDeckCards) {
                    float cardWidth = card.getCardWidth();
                    float cardHeight = card.getCardHeight();
                    float cardZ = card.getCardZ();
                    if (cardWidth > maxWidth) {
                        maxWidth = cardWidth;
                    }
                    if (cardHeight > maxHeight) {
                        maxHeight = cardHeight;
                    }
                    if (cardZ > maxElevation) {
                        maxElevation = cardZ;
                    }
                }
                cardDeckX = getXPositionForCardDeck(maxWidth, getMeasuredWidth());
                cardDeckY = getYPositionForCardDeck(maxHeight, getMeasuredHeight());
                cardDeckZ = maxElevation;
            }

            @SuppressLint("DrawAllocation")
            List<ThreeDCardCoordinates> cardsCoordinates = new CardDeckCoordinatesPattern(
                    cardDeckCards.size(),
                    cardDeckCardOffsetX,
                    cardDeckCardOffsetY,
                    cardDeckCardOffsetZ,
                    cardDeckX,
                    cardDeckY,
                    cardDeckZ)
                    .getCardsCoordinates();
            Iterator<Card> validatedCardViews = cardDeckCards.iterator();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (int i = cardsCoordinates.size() - 1; i >= 0; i--) {
                    CV card = (CV) validatedCardViews.next();
                    if (card.getVisibility() != GONE) {
                        ThreeDCardCoordinates coordinates = cardsCoordinates.get(i);
                        onLayoutCardInCardDeck(card, coordinates);
                    }
                }
            } else {
                for (int i = 0; i < cardsCoordinates.size(); i++) {
                    CV card = (CV) validatedCardViews.next();
                    if (card.getVisibility() != GONE) {
                        ThreeDCardCoordinates coordinates = cardsCoordinates.get(i);
                        onLayoutCardInCardDeck(card, coordinates);
                    }
                }
            }
        } else {
            for (int i = 0; i < cardDeckCards.size(); i++) {
                CV card = (CV) cardDeckCards.get(i);
                if (card.getVisibility() != GONE) {
                    int x = Math.round(card.getX());
                    int y = Math.round(card.getY());
                    card.layout(x, y, x + card.getMeasuredWidth(), y + card.getMeasuredHeight());
                }
            }
        }
    }

    private void onActionWithCard(CardInfo cardInfo) {
        if (onCardClickListener != null) {
            onCardClickListener.onCardAction(cardInfo);
        }
    }

    private void distributeCardForPlayer(final Layout cardsLayout,
                                         final Predicate<Card> predicate,
                                         final TableDistributedCardsListener onDistributedCardsListener) {
        List<Card> filteredCardsViews = new ArrayList<>();
        List<Card> cards = cardsLayout.getCards();
        for (Card card : cards) {
            if (predicate.apply(card) && (isDeskOfCardsEnable() || card.getVisibility() != VISIBLE)) {
                filteredCardsViews.add(card);
            }
        }
        animateCardViews(cardsLayout, filteredCardsViews.iterator(), onDistributedCardsListener);
    }

    private void animateCardViews(final Layout cardsLayout,
                                  final Iterator<Card> cardViewsIterator,
                                  final OnDistributedCardsListener onDistributedCardsListener) {
        if (cardViewsIterator.hasNext()) {
            final Card card = cardViewsIterator.next();
            onDistributedCardsListener.onStartDistributedCardWave(Collections.singletonList(card));
            animateCardView(cardsLayout, card, new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onDistributedCardsListener.onEndDistributeCardWave(Collections.singletonList(card));
                    cardsLayout.post(() -> {
                        animateCardViews(cardsLayout, cardViewsIterator, onDistributedCardsListener);
                    });
                }
            });
        } else {
            onDistributedCardsListener.onDistributedCards();
        }
    }

    private void animateCardView(Layout cardsLayout,
                                 Card card,
                                 AnimatorListenerAdapter adapter) {
        if (hasDistributionState() && deskOfCardsEnable) {
            card.getCardInfo().setCardDistributed(true);
        } else {
            card.setVisibility(VISIBLE);
        }
        cardsLayout.invalidateCardsPosition(true, creteAnimationForCardDistribution(cardsLayout, card), adapter);
    }

    private float getXPositionForCardDeck(float widthOfCardDeck, float rootWidth) {
        float cardPositionX = 0;
        if (cardDeckGravity.containsFlag(FlagManager.Gravity.LEFT)) {
            cardPositionX = 0;
        } else if (cardDeckGravity.containsFlag(FlagManager.Gravity.RIGHT)) {
            cardPositionX = rootWidth - widthOfCardDeck;
        } else if (cardDeckGravity.containsFlag(FlagManager.Gravity.CENTER_HORIZONTAL)
                || cardDeckGravity.containsFlag(FlagManager.Gravity.CENTER)) {
            cardPositionX = rootWidth / 2f - widthOfCardDeck / 2f;
        }
        return cardPositionX;
    }

    private float getYPositionForCardDeck(float heightOfCardDeck, float rootHeight) {
        float cardPositionY = 0;
        if (cardDeckGravity.containsFlag(FlagManager.Gravity.TOP)) {
            cardPositionY = 0;
        } else if (cardDeckGravity.containsFlag(FlagManager.Gravity.BOTTOM)) {
            cardPositionY = rootHeight - heightOfCardDeck;
        } else if (cardDeckGravity.containsFlag(FlagManager.Gravity.CENTER_VERTICAL)
                || cardDeckGravity.containsFlag(FlagManager.Gravity.CENTER)) {
            cardPositionY = rootHeight / 2f - heightOfCardDeck / 2f;
        }
        return cardPositionY;
    }

    private boolean hasDistributionState() {
        return viewUpdater.getParams() != null && viewUpdater.getParams().getDistributionState() != null;
    }

    private boolean isDeskOfCardsEnable() {
        return hasDistributionState() && deskOfCardsEnable;
    }
}
