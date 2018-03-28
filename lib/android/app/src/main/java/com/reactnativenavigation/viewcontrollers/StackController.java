package com.reactnativenavigation.viewcontrollers;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.v4.view.ViewPager;
import android.view.View;

import com.facebook.react.bridge.Promise;
import com.reactnativenavigation.anim.NavigationAnimator;
import com.reactnativenavigation.parse.Options;
import com.reactnativenavigation.utils.NoOpPromise;
import com.reactnativenavigation.viewcontrollers.topbar.TopBarBackgroundViewController;
import com.reactnativenavigation.viewcontrollers.topbar.TopBarController;
import com.reactnativenavigation.views.Component;
import com.reactnativenavigation.views.ReactComponent;
import com.reactnativenavigation.views.StackLayout;
import com.reactnativenavigation.views.titlebar.TitleBarReactViewCreator;
import com.reactnativenavigation.views.topbar.TopBar;

import java.util.Collection;
import java.util.Iterator;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class StackController extends ParentController<StackLayout> {

    private static final NoOpPromise NO_OP = new NoOpPromise();
    private final IdStack<ViewController> stack = new IdStack<>();
    private final NavigationAnimator animator;
    private final ReactViewCreator topBarButtonCreator;
    private final TitleBarReactViewCreator titleBarReactViewCreator;
    private TopBarBackgroundViewController topBarBackgroundViewController;
    private TopBarController topBarController;

    public StackController(final Activity activity, ReactViewCreator topBarButtonCreator, TitleBarReactViewCreator titleBarReactViewCreator, TopBarBackgroundViewController topBarBackgroundViewController, TopBarController topBarController, String id, Options initialOptions) {
        super(activity, id, initialOptions);
        this.topBarController = topBarController;
        animator = createAnimator();
        this.topBarButtonCreator = topBarButtonCreator;
        this.titleBarReactViewCreator = titleBarReactViewCreator;
        this.topBarBackgroundViewController = topBarBackgroundViewController;
    }

    public void applyOptions(Options options) {
        super.applyOptions(options);
        getView().applyChildOptions(options);
    }

    @Override
    public void applyChildOptions(Options options, Component child) {
        super.applyChildOptions(options, child);
        getView().applyChildOptions(this.options, child);
        if (child instanceof ReactComponent) {
            fabOptionsPresenter.applyOptions(this.options.fabOptions, (ReactComponent) child, getView());
        }
        applyOnParentController(parentController ->
                ((ParentController) parentController).applyChildOptions(
                        this.options.copy().clearTopBarOptions().clearAnimationOptions().clearFabOptions(),
                        child
                )
        );
        animator.setOptions(options.animationsOptions);
    }

    @Override
    public void mergeChildOptions(Options options, Component child) {
        super.mergeChildOptions(options, child);
        getView().mergeChildOptions(options, child);
        animator.mergeOptions(options.animationsOptions);
        if (options.fabOptions.hasValue() && child instanceof ReactComponent) {
            fabOptionsPresenter.mergeOptions(options.fabOptions, (ReactComponent) child, getView());
        }
        applyOnParentController(parentController ->
                ((ParentController) parentController).mergeChildOptions(
                        options.copy().clearTopBarOptions().clearAnimationOptions().clearFabOptions(),
                        child
                )
        );
    }

    @Override
    public void destroy() {
        topBarController.clear();
        super.destroy();
    }

    @Override
    void clearOptions() {
        super.clearOptions();
        topBarController.clear();
    }

    public void push(ViewController child, final Promise promise) {
        final ViewController toRemove = stack.peek();

        child.setParentController(this);
        stack.push(child.getId(), child);
        View enteringView = child.getView();
        getView().addView(enteringView, MATCH_PARENT, MATCH_PARENT);

        if (toRemove != null) {
            getView().removeView(toRemove.getView());
        }
        promise.resolve(child.getId());
    }

    public void animatePush(final ViewController child, final Promise promise) {
        final ViewController toRemove = stack.peek();

        child.setParentController(this);
        stack.push(child.getId(), child);
        View enteringView = child.getView();
        getView().addView(enteringView, MATCH_PARENT, MATCH_PARENT);

        if (toRemove != null) {
            animator.animatePush(enteringView, () -> {
                getView().removeView(toRemove.getView());
                promise.resolve(child.getId());
            });
        } else {
            promise.resolve(child.getId());
        }
    }

    void pop(final Promise promise) {
        if (!canPop()) {
            Navigator.rejectPromise(promise);
            return;
        }

        final ViewController exitingController = stack.pop();
        final ViewController enteringController = stack.peek();
        popInternal(exitingController, enteringController);

        finishPopping(exitingController.getView(), exitingController, promise);
    }

    void animatePop(final Promise promise) {
        if (!canPop()) {
            Navigator.rejectPromise(promise);
            return;
        }

        final ViewController exitingController = stack.pop();
        final ViewController enteringController = stack.peek();
        popInternal(exitingController, enteringController);

        animator.animatePop(exitingController.getView(), () -> finishPopping(exitingController.getView(),
                exitingController, promise));
    }

    private void popInternal(ViewController disappearing, ViewController appearing) {
        disappearing.onViewWillDisappear();
        appearing.onViewWillAppear();
        getView().onChildWillDisappear(disappearing.options, appearing.options, () ->
                getView().addView(appearing.getView(), getView().indexOfChild(disappearing.getView()))
        );
    }

    boolean canPop() {
        return stack.size() > 1;
    }

    private void finishPopping(View exitingView, ViewController poppedTop, Promise promise) {
        getView().removeView(exitingView);
        poppedTop.destroy();
        promise.resolve(poppedTop.getId());
    }

    void popSpecific(final ViewController childController, Promise promise) {
        if (stack.isTop(childController.getId())) {
            animatePop(promise);
        } else {
            stack.remove(childController.getId());
            childController.destroy();
            promise.resolve(childController.getId());
        }
    }

    void popTo(final ViewController viewController, Promise promise) {
        if (!stack.containsId(viewController.getId())) {
            Navigator.rejectPromise(promise);
            return;
        }

        Iterator<String> iterator = stack.iterator();
        String currentControlId = iterator.next();
        while (!viewController.getId().equals(currentControlId)) {
            String nextControlId = iterator.next();
            boolean animate = nextControlId.equals(viewController.getId());
            if (animate) {
                animatePop(promise);
            } else {
                pop(NO_OP);
            }
            currentControlId = nextControlId;
        }
    }

    void popToRoot(Promise promise) {
        while (canPop()) {
            boolean animate = stack.size() == 2; // First element is root
            if (animate) {
                animatePop(promise);
            } else {
                pop(NO_OP);
            }
        }
    }

    ViewController peek() {
        return stack.peek();
    }

    public int size() {
        return stack.size();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    @Override
    public boolean handleBack() {
        if (canPop()) {
            animatePop(NO_OP);
            return true;
        }
        return false;
    }

    @Override
    public void sendOnNavigationButtonPressed(String buttonId) {
        peek().sendOnNavigationButtonPressed(buttonId);
    }

    @NonNull
    @Override
    protected StackLayout createView() {
        return new StackLayout(getActivity(), topBarButtonCreator, titleBarReactViewCreator, topBarBackgroundViewController, topBarController, this::sendOnNavigationButtonPressed, getId());
    }

    @NonNull
    @Override
    public Collection<ViewController> getChildControllers() {
        return stack.values();
    }

    @Override
    public void setupTopTabsWithViewPager(ViewPager viewPager) {
        topBarController.initTopTabs(viewPager);
    }

    @Override
    public void clearTopTabs() {
        topBarController.clearTopTabs();
    }

     NavigationAnimator createAnimator() {
        return new NavigationAnimator(getActivity());
    }

    @RestrictTo(RestrictTo.Scope.TESTS)
    TopBar getTopBar() {
        return topBarController.getView();
    }

    @RestrictTo(RestrictTo.Scope.TESTS)
    StackLayout getStackLayout() {
        return getView();
    }
}
