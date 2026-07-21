class Handler:
    """Base handler class."""

    def process(self):
        """Process the request."""
        pass


class UserHandler(Handler):
    """Handler for user operations."""

    def process(self):
        """Process user request."""
        return "user processed"


def handle_request(data: dict):
    """Handle incoming request."""
    handler = Handler()
    return handler.process()
