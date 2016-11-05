package ua.jenshensoft.cardslayout.provider;

import android.support.v7.widget.LinearLayoutCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import ua.jenshensoft.cardslayout.util.FlagManager;
import ua.jenshensoft.cardslayout.views.CardsLayout;

import static ua.jenshensoft.cardslayout.views.CardsLayout.CircleCenterLocation.BOTTOM;
import static ua.jenshensoft.cardslayout.views.CardsLayout.CircleCenterLocation.TOP;

public class CardsCoordinatesProvider {

    private final float radius;
    @CardsLayout.CircleCenterLocation
    private final int circleCenterLocation;
    private final int cardsCount;
    @LinearLayoutCompat.OrientationMode
    private final int orientation;
    private final float[] center;
    //angles
    private final float cardSectorAngle;
    private final float startAngle;
    private final float endAngle;

    public CardsCoordinatesProvider(float radius,
                                    @CardsLayout.CircleCenterLocation int circleCenterLocation,
                                    int cardsCount,
                                    float cardWidth,
                                    float cardHeight,
                                    @LinearLayoutCompat.OrientationMode int orientation, float cardsLayoutLength,
                                    FlagManager flagManager,
                                    CardsLayout.Config xConfig,
                                    CardsLayout.Config yConfig) {
        this.circleCenterLocation = validateCircleLocation(circleCenterLocation, flagManager);
        if (cardsLayoutLength > radius * 2f) {
            radius = cardsLayoutLength / 2f;
            Log.e("CardsLayout", "Diameter can't be bigger then CardsLayoutLength");
        }
        this.radius = radius;

        this.cardsCount = cardsCount;
        this.orientation = orientation;
        this.center = getCoordinatesForCenter(orientation, this.circleCenterLocation, cardWidth, cardHeight, radius, xConfig, yConfig);

        //arcs
        final float generalArc = calcArcFromChord(radius, cardsLayoutLength);
        //angles
        final float generalAngle = calcAngleFromArc(generalArc, radius);
        final float allCardsAngle = round(generalAngle, 6);
        this.cardSectorAngle = round(allCardsAngle / (cardsCount - 1), 6);
        this.startAngle = round(90f - (generalAngle / 2f), 6);
        this.endAngle = round(90f - ((generalAngle / 2f)), 6);
    }

    public List<CardCoordinates> getCardsCoordinates() {
        List<CardCoordinates> cardsCoordinates = new ArrayList<>();
        boolean isLeftArc = true;
        float fault = 0.0001f;
        float angle = 0f;
        for (int i = 0; i < cardsCount; i++) {
            if (i == 0) {// for first card
                angle = startAngle;
            } else {
                if (isLeftArc) { //left side of arc
                    if (angle + cardSectorAngle - fault >= startAngle && angle + cardSectorAngle - fault <= 90) {
                        angle += cardSectorAngle;
                    } else if (angle + cardSectorAngle > 90) {
                        isLeftArc = false;
                        angle += cardSectorAngle;
                        angle -= 90f;
                        angle = 90f - angle;
                    } else {
                        throw new RuntimeException("Something went wrong");
                    }
                } else {
                    if (angle - cardSectorAngle + fault >= endAngle && angle - cardSectorAngle + fault <= 90) {
                        angle -= cardSectorAngle;
                    } else {
                        throw new RuntimeException("Something went wrong");
                    }
                }
            }
            final float[] coordinatesForCard = getCoordinatesForCard(angle, isLeftArc);
            cardsCoordinates.add(new CardCoordinates(coordinatesForCard[0], coordinatesForCard[1], validateAngle(angle, isLeftArc)));
        }
        return cardsCoordinates;
    }


    /* private methods */

    private int validateCircleLocation(@CardsLayout.CircleCenterLocation int circleCenterLocation, FlagManager flagManager) {
        if (flagManager.containsFlag(FlagManager.Gravity.TOP)) {
            return circleCenterLocation == BOTTOM ? TOP : BOTTOM;
        } else if (flagManager.containsFlag(FlagManager.Gravity.RIGHT)) {
            return circleCenterLocation == BOTTOM ? TOP : BOTTOM;
        }
        return circleCenterLocation;
    }

    private float[] getCoordinatesForCard(float angle, boolean isLeftArc) {
        float x;
        float y;

        if (orientation == LinearLayoutCompat.HORIZONTAL) {
            if (isLeftArc) {
                if (circleCenterLocation == TOP) {
                    x = (float) (center[0] + radius * Math.cos(Math.toRadians(angle)));
                } else {
                    x = (float) (center[0] - radius * Math.cos(Math.toRadians(angle)));
                }
            } else {
                if (circleCenterLocation == TOP) {
                    x = (float) (center[0] - radius * Math.cos(Math.toRadians(angle)));
                } else {
                    x = (float) (center[0] + radius * Math.cos(Math.toRadians(angle)));
                }
            }
            if (circleCenterLocation == TOP) {
                y = (float) (center[1] + radius * Math.sin(Math.toRadians(angle)));
            } else {
                y = (float) (center[1] - radius * Math.sin(Math.toRadians(angle)));
            }
            return new float[]{x, y};
        } else {
            if (isLeftArc) {
                if (circleCenterLocation == TOP) {
                    y = (float) (center[1] + radius * Math.cos(Math.toRadians(angle)));
                } else {
                    y = (float) (center[1] - radius * Math.cos(Math.toRadians(angle)));
                }
            } else {
                if (circleCenterLocation == TOP) {
                    y = (float) (center[1] - radius * Math.cos(Math.toRadians(angle)));
                } else {
                    y = (float) (center[1] + radius * Math.cos(Math.toRadians(angle)));
                }
            }
            if (circleCenterLocation == TOP) {
                x = (float) (center[0] - radius * Math.sin(Math.toRadians(angle)));
            } else {
                x = (float) (center[0] + radius * Math.sin(Math.toRadians(angle)));
            }
            return new float[]{x, y};
        }
    }

    private float[] getCoordinatesForCenter(@LinearLayoutCompat.OrientationMode int orientation,
                                            @CardsLayout.CircleCenterLocation int circleCenterLocation,
                                            float cardWidth,
                                            float cardHeight,
                                            float radius,
                                            CardsLayout.Config xConfig, CardsLayout.Config yConfig) {
        float x;
        float y;
        if (orientation == LinearLayoutCompat.HORIZONTAL) {
            x = xConfig.getStartCoordinates() + (xConfig.getDistanceForCards() / 2);
            if (circleCenterLocation == TOP) {
                y = yConfig.getStartCoordinates() - radius;
            } else {
                y = yConfig.getStartCoordinates() + radius;
            }
            x -= cardWidth / 2f;
            return new float[]{x, y};
        } else {
            y = yConfig.getStartCoordinates() + (yConfig.getDistanceForCards() / 2);
            if (circleCenterLocation == TOP) {
                x = xConfig.getStartCoordinates() + radius;
            } else {
                x = xConfig.getStartCoordinates() - radius;
            }
            y -= cardHeight / 2f;
            return new float[]{x, y};
        }
    }

    private float validateAngle(float angle, boolean isLeftArc) {
        if (isLeftArc) {
            return -90f + angle;
        } else {
            return 90f - angle;
        }
    }

    private float calcAngleFromArc(float arc, float radius) {
        return (float) Math.toDegrees(arc / radius);
    }

    private float calcArc(float radius, float angleDegrees) {
        return (float) ((Math.PI * radius) / 180f * angleDegrees);
    }

    private float calcArcFromChord(float radius, float сhordLenght) {
        float angleDegrees = (float) Math.toDegrees(Math.asin((сhordLenght / 2f) / radius)) * 2f;
        return calcArc(radius, angleDegrees);
    }

    private float round(float number, int scale) {
        int pow = 10;
        for (int i = 1; i < scale; i++)
            pow *= 10;
        float tmp = number * pow;
        return (float) (int) ((tmp - (int) tmp) >= 0.5f ? tmp + 1 : tmp) / pow;
    }
}