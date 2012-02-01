package com.fanfou.app;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

import com.fanfou.app.App.ApnType;
import com.fanfou.app.adapter.UserCursorAdapter;
import com.fanfou.app.api.User;
import com.fanfou.app.db.Contents.BasicColumns;
import com.fanfou.app.db.Contents.UserInfo;
import com.fanfou.app.service.Constants;
import com.fanfou.app.service.FanFouService;
import com.fanfou.app.ui.ActionBar;
import com.fanfou.app.ui.ActionManager;
import com.fanfou.app.ui.TextChangeListener;
import com.fanfou.app.util.StringHelper;
import com.fanfou.app.util.Utils;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;

/**
 * @author mcxiaoke
 * @version 1.0 2011.06.10
 * @version 1.5 2011.10.29
 * @version 1.6 2011.11.07
 * @version 2.0 2011.11.07
 * @version 2.1 2011.11.09
 * @version 2.2 2011.11.18
 * @version 2.3 2011.11.21
 * @version 2.4 2011.12.13
 * @version 2.5 2011.12.23
 * @version 3.0 2012.01.30
 * @version 3.1 2012.01.31
 * 
 */
public class UserListPage extends BaseActivity implements OnRefreshListener,
		FilterQueryProvider, OnItemClickListener {
	private static final String TAG = UserListPage.class.getSimpleName();

	protected ActionBar mActionBar;
	protected PullToRefreshListView mPullToRefreshListView;
	private ListView mList;

	protected EditText mEditText;

	protected Cursor mCursor;
	protected UserCursorAdapter mCursorAdapter;

	protected String userId;
	protected String userName;
	protected User user;
	protected int type;

	protected int page = 1;
	
	private boolean initialized=false;

	private static final String tag = UserListPage.class.getSimpleName();

	private void log(String message) {
		Log.d(tag, message);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (App.DEBUG)
			log("onCreate");
		if (parseIntent()) {
			initialize();
			setLayout();
			initCheckState();
		} else {
			finish();
		}
	}

	protected void initialize() {
		initCursor();
	}

	protected void initCursor() {
		String where = BasicColumns.TYPE + "=? AND " + BasicColumns.OWNER_ID
				+ "=?";
		String[] whereArgs = new String[] { String.valueOf(type), userId };
		mCursor = managedQuery(UserInfo.CONTENT_URI, UserInfo.COLUMNS, where,
				whereArgs, null);
	}

	protected void initCheckState() {
		if (mCursor.getCount() == 0) {
			onRefresh();
			mPullToRefreshListView.setRefreshing();
		} else {
			initialized=true;
			mEditText.setVisibility(View.VISIBLE);
		}
	}

	private void setLayout() {
		setContentView(R.layout.list_users);

		setActionBar();

		mEditText = (EditText) findViewById(R.id.choose_input);
		mEditText.addTextChangedListener(new MyTextWatcher());

		mPullToRefreshListView = (PullToRefreshListView) findViewById(R.id.list);
		mPullToRefreshListView.setOnRefreshListener(this);
		mList = mPullToRefreshListView.getRefreshableView();
		mList.setOnItemClickListener(this);

		mCursorAdapter = new UserCursorAdapter(mContext, mCursor);
		mCursorAdapter.setFilterQueryProvider(this);

		mList.setAdapter(mCursorAdapter);
	}

	private class MyTextWatcher extends TextChangeListener {
		@Override
		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
			mCursorAdapter.getFilter().filter(s.toString().trim());
		}
	}

	/**
	 * 初始化和设置ActionBar
	 */
	private void setActionBar() {
		mActionBar = (ActionBar) findViewById(R.id.actionbar);
		setActionBarSwipe(mActionBar);
		if (user != null) {
			if (type == Constants.TYPE_USERS_FRIENDS) {
				mActionBar.setTitle(user.screenName + "关注的人");
			} else if (type == Constants.TYPE_USERS_FOLLOWERS) {
				mActionBar.setTitle("关注" + user.screenName + "的人");
			}
		}
	}

	protected boolean parseIntent() {
		Intent intent = getIntent();
		type = intent.getIntExtra(Constants.EXTRA_TYPE,
				Constants.TYPE_USERS_FRIENDS);
		user = (User) intent.getParcelableExtra(Constants.EXTRA_DATA);
		if (user == null) {
			userId = intent.getStringExtra(Constants.EXTRA_ID);
		} else {
			userId = user.id;
			userName = user.screenName;
		}
		return !StringHelper.isEmpty(userId);
	}

	protected void doRefresh() {
		page = 1;
		doRetrieve(false);
	}

	protected void doGetMore() {
		page++;
		doRetrieve(true);
	}

	protected void doRetrieve(boolean isGetMore) {
		if (userId == null) {
			if (App.DEBUG)
				log("userId is null");
			return;
		}
		final Handler handler = new ResultHandler(isGetMore);
		if (type == Constants.TYPE_USERS_FRIENDS) {
			FanFouService.doFetchFriends(this, handler, page, userId);
		} else {
			FanFouService.doFetchFollowers(this, handler, page, userId);
		}
	}

	protected void updateUI() {
		if (App.DEBUG) {
			log("updateUI()");
		}
		if (mCursor != null) {
			mCursor.requery();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (App.getApnType() != ApnType.WIFI) {
			App.getImageLoader().clearQueue();
		}
	}

	private static final String LIST_STATE = "listState";
	private Parcelable mState = null;

	@Override
	protected void onResume() {
		super.onResume();
		if (mState != null && mList != null) {
			mList.onRestoreInstanceState(mState);
			mState = null;
		}
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		mState = savedInstanceState.getParcelable(LIST_STATE);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mList != null) {
			mState = mList.onSaveInstanceState();
			outState.putParcelable(LIST_STATE, mState);
		}
	}

	protected class ResultHandler extends Handler {
		private boolean doGetMore;

		public ResultHandler(boolean doGetMore) {
			this.doGetMore = doGetMore;
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Constants.RESULT_SUCCESS:
				int count = msg.getData().getInt(Constants.EXTRA_COUNT);
				updateUI();
				mPullToRefreshListView.onRefreshComplete();
				if(!initialized){
					mEditText.setVisibility(View.VISIBLE);
				}
				break;
			case Constants.RESULT_ERROR:
				if(!initialized){
					mEditText.setVisibility(View.VISIBLE);
				}
				String errorMessage = msg.getData().getString(
						Constants.EXTRA_ERROR);
				int errorCode = msg.getData().getInt(Constants.EXTRA_CODE);
				mPullToRefreshListView.onRefreshComplete();
				Utils.notify(mContext, errorMessage);
				Utils.checkAuthorization(mContext, errorCode);
				break;
			default:
				break;
			}
		}

	}

	@Override
	public void onRefresh() {
		boolean fromTop = mPullToRefreshListView.hasPullFromTop();
		if (App.DEBUG) {
			Log.d(TAG, "onRefresh() top=" + fromTop);
		}

		if (fromTop) {
			doRefresh();
		} else {
			doGetMore();
		}
	}

	@Override
	public Cursor runQuery(CharSequence constraint) {
		String where = BasicColumns.TYPE + " = " + type + " AND "
				+ BasicColumns.OWNER_ID + " = '" + userId + "' AND ("
				+ UserInfo.SCREEN_NAME + " like '%" + constraint + "%' OR "
				+ BasicColumns.ID + " like '%" + constraint + "%' )";
		;
		return managedQuery(UserInfo.CONTENT_URI, UserInfo.COLUMNS, where,
				null, null);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		final Cursor c = (Cursor) parent.getItemAtPosition(position);
		final User u = User.parse(c);
		if (u != null) {
			if (App.DEBUG)
				log("userId=" + u.id + " username=" + u.screenName);
			ActionManager.doProfile(mContext, u);
		}
	}

}
