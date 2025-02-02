import React, {createRef} from 'react';
import {withRouter} from 'react-router-dom';
import _, {connect, ConnectedProps} from 'react-redux';

import InfiniteLoader from 'react-window-infinite-loader';
import ResizableWindowList from '../../../components/ResizableWindowList';

import {newestConversationFirst, newestFilteredConversationFirst} from '../../../selectors/conversations';
import {fetchNextConversationPage} from '../../../actions/conversations';
import {fetchNextFilteredPage} from '../../../actions/conversationsFilter';

import ConversationListHeader from '../ConversationListHeader';
import QuickFilter from '../QuickFilter';
import ConversationListItem from '../ConversationListItem';
import NoConversations from '../NoConversations';

import {MergedConversation, StateModel} from '../../../reducers';

import styles from './index.module.scss';
import {ConversationRouteProps} from '../index';

type ConversationListProps = ConnectedProps<typeof connector>;

const mapDispatchToProps = {
  fetchNext: fetchNextConversationPage,
  fetchNextFiltered: fetchNextFilteredPage,
};

const mapStateToProps = (state: StateModel, ownProps: ConversationRouteProps) => ({
  currentConversationId: ownProps.match.params.conversationId,
  conversations: newestConversationFirst(state),
  filteredConversations: newestFilteredConversationFirst(state),
  conversationsPaginationData: state.data.conversations.all.paginationData,
  filteredPaginationData: state.data.conversations.filtered.paginationData,
  currentFilter: state.data.conversations.filtered.currentFilter,
  user: state.data.user,
});

const connector = connect(mapStateToProps, mapDispatchToProps);

const ConversationList = (props: ConversationListProps) => {
  const listRef: any = createRef();

  const renderConversationItem = (conversation: MergedConversation, style: React.CSSProperties) => {
    const {currentConversationId} = props;
    if (conversation == null) {
      return <div />;
    }
    return (
      <ConversationListItem
        style={style}
        key={conversation.id}
        conversation={conversation}
        active={conversation.id === currentConversationId}
      />
    );
  };

  const renderConversationList = () => {
    const {
      conversations,
      filteredConversations,
      conversationsPaginationData,
      filteredPaginationData,
      currentFilter,
      fetchNext,
      fetchNextFiltered,
    } = props;

    const hasFilter = Object.keys(currentFilter || {}).length > 0;
    const items = hasFilter ? filteredConversations : conversations;
    const paginationData = hasFilter ? filteredPaginationData : conversationsPaginationData;

    const hasMoreData = paginationData.nextCursor && paginationData.nextCursor.length > 0;
    const loading = paginationData.loading;

    const isItemLoaded = (index: number) => index < items.length;
    const itemCount = hasMoreData ? items.length + 1 : items.length;
    const loadMoreItems = () => {
      if (!loading) {
        hasFilter ? fetchNextFiltered() : fetchNext();
      }
      return Promise.resolve(true);
    };

    return (
      <InfiniteLoader isItemLoaded={isItemLoaded} itemCount={itemCount} loadMoreItems={loadMoreItems}>
        {({onItemsRendered, ref}) => (
          <div className={styles.conversationListPaginationWrapper}>
            {!items.length && !loading ? (
              <NoConversations conversations={conversations.length} filterSet={!!Object.keys(currentFilter).length} />
            ) : (
              <ResizableWindowList
                ref={listRef}
                infiniteLoaderRef={ref}
                itemCount={itemCount}
                itemSize={115}
                width={'100%'}
                onItemsRendered={onItemsRendered}>
                {({index, style}) => renderConversationItem(items[index], style)}
              </ResizableWindowList>
            )}
          </div>
        )}
      </InfiniteLoader>
    );
  };

  return (
    <section className={styles.conversationListContainerContacts}>
      <div className={styles.conversationListContainer}>
        <section className={styles.conversationListContainerFilterBox}>
          <ConversationListHeader />
          <QuickFilter />
        </section>
      </div>
      <section className={styles.conversationListContactList}>{renderConversationList()}</section>
    </section>
  );
};

export default withRouter(connector(ConversationList));
