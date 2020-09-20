package ru.otus.feeds;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.rometools.rome.io.FeedException;
import lombok.extern.slf4j.Slf4j;

import ru.otus.model.Feed;
import ru.otus.model.Message;

@Slf4j
public class MultiFeedReader implements AutoCloseable {
    private final ExecutorService executorService;
    private final FeedReaderProperties readerProperties;
    private final LinkedBlockingQueue<Message> messageQueue;
    private final AtomicBoolean closed = new AtomicBoolean();

    public MultiFeedReader(FeedReaderProperties readerProperties, List<Feed> feeds) {
        this.readerProperties = readerProperties;
        executorService = Executors.newCachedThreadPool();
        messageQueue = new LinkedBlockingQueue<>();
        for (var feed : feeds) {
            addFeed(feed);
        }
    }

    public boolean checkUrl(String url) {
        return new SingleFeedReader(Feed.NewFeed(url, new HashSet<>())).checkUrl();
    }

    public void addFeed(Feed feed) {
        executorService.submit(() -> readFeed(feed));
    }

    public List<Message> getUpdates() {
        List<Message> updates = new ArrayList<>();
        var message = messageQueue.poll();
        while (message != null) {
            updates.add(message);
            message = messageQueue.poll();
        }
        return updates;
    }

    public void close() throws Exception {
        closed.set(true);
        executorService.awaitTermination(readerProperties.getShutdownTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private void readFeed(Feed feed) {
        log.info("Starting to read feed {}", feed);
        while (!closed.get()) {
            try {
                var reader = new SingleFeedReader(feed);
                while (!closed.get()) {
                    log.info("Checking {}", feed);
                    if (feed.getChats().isEmpty()) {
                        log.info("No chats for feed {}", feed);
                        return;
                    }
                    writeUpdates(reader.getUpdates());
                    Thread.sleep(readerProperties.getUpdateInterval().toMillis());
                }
            } catch (IOException | FeedException e) {
                log.warn("Execution exception", e);
            } catch (InterruptedException e) {
                log.info("Reader {} thread was interrupted", feed);
                Thread.currentThread().interrupt();
            }
        }
    }

    private void writeUpdates(List<Message> updates) throws InterruptedException {
        var queueOfferInterval = readerProperties.getQueueOfferInterval().toMillis();
        for (var message : updates) {
            while (!closed.get()) {
                if (messageQueue.offer(message, queueOfferInterval, TimeUnit.MILLISECONDS)) {
                    break;
                }
            }
        }
    }
}
