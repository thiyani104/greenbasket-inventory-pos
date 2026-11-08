package model;

/** Can sell and look up stock, but not change the catalogue, see costs or void sales. */
public class SalesAssistant extends User {

    public SalesAssistant(int userId, String username, String fullName, String passwordHash) {
        super(userId, username, fullName, passwordHash);
    }

    @Override public Role getRole() { return Role.ASSISTANT; }
}
