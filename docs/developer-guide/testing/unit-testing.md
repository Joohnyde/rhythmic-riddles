# Unit Testing in Spring Boot

## What unit tests are for

Unit tests verify small pieces of backend logic in isolation.

In this project, they are used to protect the core game rules without needing a real database, real websocket connections, or the full application to start.

We need them because they:

- catch regressions early
- make refactoring safer
- document expected behavior
- validate rule-heavy logic such as stage changes, interrupts, scoring, and context generation

## What we use

The backend unit tests use:

- **JUnit 5** — the test framework
- **Mockito** — mocking and verification
- **Maven Surefire** — running tests through Maven

Typical command:

```bash
mvn test
```

Run one test class:

```bash
mvn -Dtest=GameServiceImplTest test
```


## What a unit test is in Spring Boot

A unit test should test one class, usually one method or one behavior branch, without starting the whole application.

For service-layer tests, that usually means:

- create the service under test
- mock its dependencies
- prepare input data
- call the method
- assert the result
- verify side effects if needed

Example shape:

```java
@ExtendWith(MockitoExtension.class)
class GameServiceImplTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private BroadcastGateway broadcastGateway;

    @InjectMocks
    private GameServiceImpl gameService;

    @Test
    void findByCodeReturnsGameWhenPresent() {
        GameEntity game = new GameEntity();
        game.setCode("AKKU");

        when(gameRepository.findByCode("AKKU", 0)).thenReturn(game);

        GameEntity result = gameService.findByCode("AKKU", 0);

        assertEquals("AKKU", result.getCode());
    }
}
```


## Core annotations you will use

### `@ExtendWith(MockitoExtension.class)`
Enables Mockito in JUnit 5 tests.

Without it, `@Mock` and `@InjectMocks` will not be initialized automatically.

### `@Mock`
Creates a fake dependency.

Example:

```java
@Mock
private GameRepository gameRepository;
```

This lets you control what the repository returns without touching a real database.

### `@InjectMocks`
Creates the class under test and injects the mocks into it.

Example:

```java
@InjectMocks
private GameServiceImpl gameService;
```

### `@Test`
Marks a method as a test.


## The normal flow of writing a test

A very good pattern is:

### 1. Arrange
Prepare test data and mock behavior.

```java
GameEntity game = new GameEntity();
game.setCode("AKKU");

when(gameRepository.findByCode("AKKU", 0)).thenReturn(game);
```

### 2. Act
Call the method you want to test.

```java
GameEntity result = gameService.findByCode("AKKU", 0);
```

### 3. Assert
Check the result and side effects.

```java
assertEquals("AKKU", result.getCode());
```

This is often called **AAA**:
- Arrange
- Act
- Assert


## The most common Mockito functions

## `when(...)`
Defines what a mock should return when called.

Example:

```java
when(gameRepository.findByCode("AKKU", 0)).thenReturn(game);
```

Meaning:
when this method is called with these arguments, return this object.


## `thenReturn(...)`
Used after `when(...)` to specify the returned value.

Example:

```java
when(teamRepository.findByRoomCode("AKKU")).thenReturn(List.of());
```


## `thenThrow(...)`
Used after `when(...)` to make the mock throw an exception.

Example:

```java
when(gameRepository.findByCode("AKKU", 2)).thenThrow(new GameNotFoundException("Game not found"));
```

Useful for testing failure paths.


## `verify(...)`
Checks whether a mocked dependency was called.

Example:

```java
verify(gameRepository).save(game);
```

This verifies that `save(game)` happened.


## `never()`
Used with `verify(...)` to ensure something did not happen.

Example:

```java
verify(broadcastGateway, never()).broadcast(any());
```

Very useful in rejected-operation tests.


## `times(n)`
Checks how many times something happened.

Example:

```java
verify(scheduleRepository, times(2)).findLastPlayed(anyLong());
```


## `any()`
A matcher meaning “any value of this type is acceptable”.

Example:

```java
when(interruptRepository.findActive(anyLong())).thenReturn(interrupt);
```

For typed usage:

```java
anyLong()
anyInt()
anyString()
any(LocalDateTime.class)
```

Use this when the exact argument is not important for the test.


## `eq(...)`
A matcher meaning “this exact value”.

Example:

```java
verify(interruptRepository).resolveErrors(eq(lastPlayed.getId()), any(LocalDateTime.class));
```

This is often needed when mixing an exact value with matchers.

### Important rule
If you use a matcher for one argument, you must use matchers for all arguments.

Wrong:

```java
verify(interruptRepository).resolveErrors(lastPlayed.getId(), any(LocalDateTime.class));
```

Correct:

```java
verify(interruptRepository).resolveErrors(eq(lastPlayed.getId()), any(LocalDateTime.class));
```


## `isNull()` and `notNull()`
Matchers for null checks.

Example:

```java
verify(gameRepository).save(notNull());
```


## `argThat(...)`
Lets you verify a more complex condition on an argument.

Example:

```java
verify(broadcastGateway).broadcast(argThat(message ->
    "AKKU".equals(message.getRoomCode()) && message.getStage() == 2
));
```

Very useful when checking DTO payload content.


## `ArgumentCaptor`
Captures the actual object passed to a mock so you can inspect it in detail.

Example:

```java
ArgumentCaptor<GameContextMessage> captor =
    ArgumentCaptor.forClass(GameContextMessage.class);

verify(broadcastGateway).broadcast(captor.capture());

GameContextMessage sent = captor.getValue();
assertEquals("AKKU", sent.getRoomCode());
assertEquals(2, sent.getStage());
```

Use this when `argThat(...)` becomes too hard to read.


## `InOrder`
Verifies call order.

Example:

```java
InOrder inOrder = inOrder(gameRepository, broadcastGateway);

inOrder.verify(gameRepository).save(game);
inOrder.verify(broadcastGateway).broadcast(any());
```

Use this only when order truly matters.


## Common assertion functions

### `assertEquals(expected, actual)`
Checks exact equality.

### `assertTrue(...)` / `assertFalse(...)`
Checks boolean conditions.

### `assertNotNull(...)` / `assertNull(...)`
Checks whether something exists.

### `assertThrows(...)`
Checks that an exception is thrown.

Example:

```java
GameNotFoundException ex = assertThrows(
    GameNotFoundException.class,
    () -> gameService.findByCode("AKKU", 2)
);

assertEquals("Game not found", ex.getMessage());
```

This is very important. Do not only assert that an exception happens. Also assert its type and, when helpful, its message.


## What to test

For each public service method, usually test:

- success path
- failure path
- edge case
- side effects

For rule-heavy methods, also test:

- repeated calls
- invalid state
- missing dependencies
- no-op situations
- exact broadcast content
- order of side effects


## Good unit test example

```java
@Test
void changeStagePersistsStateAndBroadcastsContext() throws Exception {
    GameEntity game = game("AKKU", 1);

    when(gameRepository.findByCode("AKKU", 1)).thenReturn(game);

    gameService.changeStage("AKKU", 2);

    assertEquals(2, game.getStageId());
    verify(gameRepository).save(game);
    verify(broadcastGateway).broadcast(any());
}
```

Why this is good:

- focused on one behavior
- clear name
- checks result and side effect
- small setup


## Bad unit test example

```java
@Test
void test1() throws Exception {
    when(gameRepository.findByCode(anyString(), anyInt())).thenReturn(game("AKKU", 1));
    var result = gameService.changeStage("AKKU", 2);
    assertNotNull(result);
    verify(gameRepository).save(any());
    verify(broadcastGateway).broadcast(any());
}
```

Problems:

- bad test name
- vague assertions
- unclear intention
- too matcher-heavy
- does not explain the expected business behavior


## How to name tests

Use names that describe behavior.

Good:

```java
changeStageRejectsIllegalTransition
contextFetchReturnsStageTwoContextWhenSongIsActive
interruptRejectsDuplicateAttemptForSameSong
progressResolvesErrorsBeforeBroadcastingNextSong
```

Bad:

```java
testChangeStage
test1
worksFine
```

A good test name should explain:
- what method/behavior
- under what condition
- what should happen


## Common pitfalls

## 1. Mixing matchers and raw values
Wrong:

```java
verify(repo).resolveErrors(lastPlayed.getId(), any(LocalDateTime.class));
```

Correct:

```java
verify(repo).resolveErrors(eq(lastPlayed.getId()), any(LocalDateTime.class));
```


## 2. Unnecessary stubbing
If you stub something that is never called, Mockito may throw `UnnecessaryStubbingException`.

Example of unnecessary stub:

```java
when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);
```

If the method throws before reaching that line, the stub is unused.

Fix:
- remove the stub, or
- fix the setup so the code actually reaches that branch

Do not use `lenient()` unless there is a very good reason.


## 3. Over-mocking
Do not mock everything blindly.

Mock dependencies of the class under test, not the class under test itself.

Good:
- mock repositories
- mock gateways
- test real service logic

Bad:
- mock the service and then “test” that mocked behavior


## 4. Asserting too little
This is weak:

```java
assertNotNull(result);
```

Prefer stronger checks:

```java
assertEquals(2, result.getStage());
assertEquals("AKKU", result.getRoomCode());
```


## 5. Testing implementation instead of behavior
Do not write tests that only prove that internal private details exist.

Test:
- outcomes
- side effects
- business rules

Avoid brittle tests that break because of harmless refactoring.


## 6. One test doing too much
A test should ideally check one behavior branch.

If a test validates five different branches, debugging failures becomes painful.


## 7. Copy-paste setup everywhere
If many tests create the same data in the same way, move that into helper methods or a shared factory.


## How to handle generated test data

Your project already uses helper methods like:

- `game(...)`
- `projection(...)`
- schedule/test entity builders
- interrupt/team/category factory helpers

This is the right idea.

Instead of repeating this in many files, centralize it.

### Recommended approach
Create a shared helper class, for example:

```java
public final class ServiceImplTestDataFactory {

    private ServiceImplTestDataFactory() {}

    public static GameEntity game(String roomCode, Integer stageId) {
        GameEntity game = new GameEntity();
        game.setCode(roomCode);
        game.setStageId(stageId);
        return game;
    }

    public static TeamProjection projection(Long id, String name, Integer score) {
        return new TeamProjection() {
            @Override public Long getId() { return id; }
            @Override public String getName() { return name; }
            @Override public Integer getScore() { return score; }
        };
    }
}
```

Then use it in tests:

```java
GameEntity game = ServiceImplTestDataFactory.game("AKKU", 2);
```

### Why this helps
- less duplication
- easier maintenance
- consistent test data
- simpler migration when entities change


## How to organize tests by file

Best structure for your project:

- one file per ServiceImpl
- inside the file, group methods by functionality

Examples of functional suites:

- `ContextFetch`
- `StageTransitions`
- `InterruptValidation`
- `AnswerResolution`
- `Broadcasting`
- `Recovery`
- `Progression`

The grouping can be done by comments, regions, or helper naming conventions. The important thing is that a developer can quickly find the relevant test area.


## How to decide whether a test is worth keeping

A strong test usually scores high if it protects:

- core game flow
- interrupts
- scoring
- stage transitions
- context generation
- recovery logic
- side-effect ordering

A weaker or more redundant test might:
- duplicate another scenario almost exactly
- verify a trivial wrapper method
- assert something already proven elsewhere in a more useful way

Do not remove tests just because there are many. Remove only when they are truly duplicated or too low-value.


## What to avoid in this project specifically

Because this project is state-heavy, avoid:

- weak assertions in `contextFetch` tests
- missing negative broadcast assertions
- missing repeated-call tests
- ignoring corrupted or inconsistent state
- only testing happy paths for interrupt logic

The dangerous bugs in this codebase usually appear in:
- unusual state combinations
- invalid transitions
- resume/recovery behavior
- duplicate actions
- mismatched persisted state


## Practical checklist for each new service test

Before finishing a new test, ask:

- Does the test name explain the behavior clearly?
- Is the setup minimal but complete?
- Am I testing behavior, not internals?
- Did I assert the important output?
- Did I verify important side effects?
- If it fails, will the reason be obvious?

If the answer is yes to all of these, the test is probably solid.


## Summary

In this project, unit tests are mainly used to protect the backend service layer.

Use:
- JUnit 5 for test structure and assertions
- Mockito for mocks, stubbing, and verification

Write tests that are:
- behavior-focused
- readable
- small
- strict about business rules

For core flows, always think beyond the happy path:
- failures
- edge cases
- repeated calls
- broadcast suppression
- corrupted state

That is how unit tests become a real regression safety net instead of just extra code.
