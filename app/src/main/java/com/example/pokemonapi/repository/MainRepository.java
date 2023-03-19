package com.example.pokemonapi.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.pokemonapi.App;
import com.example.pokemonapi.database.PokemonListDAO;
import com.example.pokemonapi.model.pokemonlist.PokemonListAPI;
import com.example.pokemonapi.model.pokemonlist.ResultsResponse;
import com.example.pokemonapi.network.APIClient;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.DisposableObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MainRepository {

    @Inject
    APIClient apiClient;
    @Inject
    PokemonListDAO pokemonListDAO;
    private final List<ResultsResponse> pokemonList = new ArrayList<>();
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final static MutableLiveData<List<ResultsResponse>> pokemonListLiveData = new MutableLiveData<>();
    private final static MutableLiveData<Boolean> progressBarLiveData = new MutableLiveData<>();
    private final static MutableLiveData<Boolean> swipeRefreshLayoutLiveData = new MutableLiveData<>();
    private final static MutableLiveData<String> toastLiveData = new MutableLiveData<>();

    public MainRepository() {
        getInjection();
    }

    public void fetchPokemonList(int offset) {
        progressBarLiveData.setValue(true);

        Observable<PokemonListAPI> pokemonListAPIObservable = apiClient.observableFetchPokemonList(offset);
        DisposableObserver<PokemonListAPI> pokemonListAPIObserver = getPokemonListAPIObserver(offset);

        Disposable disposableFetchData = pokemonListAPIObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(pokemonListAPIObserver);

        compositeDisposable.add(disposableFetchData);
    }

    private DisposableObserver<PokemonListAPI> getPokemonListAPIObserver(int offset) {
        return new DisposableObserver<PokemonListAPI>() {
            @Override
            public void onNext(@NonNull PokemonListAPI pokemonListAPI) {
                onResponseSuccess(pokemonListAPI, offset);
            }

            @Override
            public void onError(@NonNull Throwable e) {
                onResponseFail(e, offset);
            }

            @Override
            public void onComplete() {

            }
        };
    }

    private void onResponseSuccess(PokemonListAPI pokemonListAPI, int offset) {
        pokemonList.clear();

        List<ResultsResponse> resultsList = pokemonListAPI.getResults();

        for (int i = 0; i < resultsList.size(); i++) {
            ResultsResponse result = resultsList.get(i);

            String namePoke = result.getName();

            String urlPoke = result.getUrl().replaceFirst(".$", "").substring(33);

            pokemonList.add(new ResultsResponse(offset, namePoke, urlPoke));
        }

        progressBarLiveData.setValue(false);
        pokemonListLiveData.setValue(pokemonList);
        swipeRefreshLayoutLiveData.setValue(true);
        onInsertPokemonListIntoDatabase(pokemonList);
    }

    private void onResponseFail(Throwable e, int offset) {
        progressBarLiveData.setValue(false);

        if (pokemonListDAO.getPokemonList(offset).isEmpty()) {
            toastLiveData.setValue(e.getMessage());
        } else {
            pokemonListLiveData.setValue(pokemonListDAO.getPokemonList(offset));
            swipeRefreshLayoutLiveData.setValue(true);
            toastLiveData.setValue("");
        }
    }

    private void onInsertPokemonListIntoDatabase(List<ResultsResponse> pokemonList) {
        Observable<List<ResultsResponse>> observable = Observable.just(pokemonList);

        Disposable disposableInsertData = observable
                .doOnNext(resultsResponses -> pokemonListDAO.insertPokemonList(resultsResponses))
                .subscribeOn(Schedulers.io())
                .subscribe();

        compositeDisposable.add(disposableInsertData);
    }

    private void getInjection() {
        App.getAppComponent().injectMainRepository(this);
    }

    public static LiveData<List<ResultsResponse>> getPokemonListLiveData() {
        return pokemonListLiveData;
    }

    public static LiveData<Boolean> getProgressBarLiveData() {
        return progressBarLiveData;
    }

    public static LiveData<Boolean> getSwipeRefreshLayoutLiveData() {
        return swipeRefreshLayoutLiveData;
    }

    public static LiveData<String> getToastLiveData() {
        return toastLiveData;
    }

    public void resetValuesLiveData() {
        pokemonListLiveData.postValue(null);
        progressBarLiveData.postValue(null);
        swipeRefreshLayoutLiveData.postValue(null);
        toastLiveData.postValue(null);
    }

    public void getDisposableToUnsubscribe() {
        compositeDisposable.dispose();
    }
}
